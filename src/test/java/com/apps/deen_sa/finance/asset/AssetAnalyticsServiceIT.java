package com.apps.deen_sa.finance.asset;

import com.apps.deen_sa.IntegrationTestBase;
import com.apps.deen_sa.core.state.StateChangeEntity;
import com.apps.deen_sa.core.state.StateChangeRepository;
import com.apps.deen_sa.core.state.StateChangeTypeEnum;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.state.StateContainerRepository;
import com.apps.deen_sa.core.state.CompletenessLevelEnum;
import com.apps.deen_sa.dto.AssetMetricsDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AssetAnalyticsService (Phase 4)
 * 
 * Tests verify:
 * - Multiple BUYs → correct average price calculation
 * - BUY + SELL → correct realized PnL calculation
 * - SELL without BUY → handled safely
 * - No side effects on containers or transactions (read-only)
 * - Edge cases (no transactions, partial sells, etc.)
 */
public class AssetAnalyticsServiceIT extends IntegrationTestBase {

    @Autowired
    AssetAnalyticsService assetAnalyticsService;

    @Autowired
    StateContainerRepository containerRepository;

    @Autowired
    StateChangeRepository transactionRepository;

    @Autowired
    com.apps.deen_sa.core.mutation.StateMutationRepository mutationRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    @AfterEach
    void cleanupTestData() {
        transactionTemplate.execute(status -> {
            mutationRepository.deleteAll();
            transactionRepository.deleteAll();
            containerRepository.deleteAll();
            return null;
        });
    }

    /**
     * TEST CASE 1: Multiple BUYs → Correct Average Price
     * 
     * User makes multiple BUY transactions at different prices.
     * 
     * EXPECTED:
     * - averageBuyPrice = totalBuyAmount / totalBuyQuantity
     * - totalQuantity = sum of all BUY quantities
     * - investedAmount = sum of all BUY amounts
     * - realizedPnL = null (no SELL transactions)
     */
    @Test
    void multipleBuys_correctAveragePrice() {
        
        // Setup: Create asset container and transactions
        transactionTemplate.execute(status -> {
            
            // Create asset container
            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("ITC");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(BigDecimal.ZERO);
            assetContainer.setUnit("shares");
            assetContainer.setOpenedAt(Instant.now());
            assetContainer = containerRepository.save(assetContainer);

            // BUY 1: 10 shares at 100 = 1000
            createBuyTransaction(assetContainer, new BigDecimal("10"), new BigDecimal("100"));

            // BUY 2: 20 shares at 150 = 3000
            createBuyTransaction(assetContainer, new BigDecimal("20"), new BigDecimal("150"));

            // BUY 3: 30 shares at 200 = 6000
            createBuyTransaction(assetContainer, new BigDecimal("30"), new BigDecimal("200"));

            return null;
        });

        // Execute: Compute metrics
        AssetMetricsDto metrics = assetAnalyticsService.computeMetrics("ITC", 1L);

        // Verify: Total quantity = 10 + 20 + 30 = 60
        assertEquals(0, new BigDecimal("60").compareTo(metrics.getTotalQuantity()));

        // Verify: Total invested = 1000 + 3000 + 6000 = 10000
        assertEquals(0, new BigDecimal("10000").compareTo(metrics.getInvestedAmount()));

        // Verify: Average buy price = 10000 / 60 = 166.6667 (rounded to 4 decimal places)
        assertEquals(0, new BigDecimal("166.6667").compareTo(metrics.getAverageBuyPrice()));

        // Verify: No SELL transactions, so realizedPnL is null
        assertNull(metrics.getRealizedPnL());

        // Verify: soldAmount = 0
        assertEquals(0, BigDecimal.ZERO.compareTo(metrics.getSoldAmount()));
        
        // Verify: Asset identifier matches
        assertEquals("ITC", metrics.getAssetIdentifier());
    }

    /**
     * TEST CASE 2: BUY + SELL → Correct Realized PnL
     * 
     * User buys asset and then sells a portion.
     * 
     * EXPECTED:
     * - realizedPnL = soldAmount - (soldQuantity × averageBuyPrice)
     * - totalQuantity = buyQuantity - sellQuantity
     */
    @Test
    void buyAndSell_correctRealizedPnL() {
        
        // Setup: Create asset container and transactions
        transactionTemplate.execute(status -> {
            
            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("Gold");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(BigDecimal.ZERO);
            assetContainer.setUnit("grams");
            assetContainer.setOpenedAt(Instant.now());
            assetContainer = containerRepository.save(assetContainer);

            // BUY 1: 100 grams at 5000 = 500000
            createBuyTransaction(assetContainer, new BigDecimal("100"), new BigDecimal("5000"));

            // BUY 2: 50 grams at 6000 = 300000
            createBuyTransaction(assetContainer, new BigDecimal("50"), new BigDecimal("6000"));

            // Total invested = 800000
            // Total quantity = 150 grams
            // Average price = 800000 / 150 = 5333.3333

            // SELL: 60 grams at 7000 = 420000
            createSellTransaction(assetContainer, new BigDecimal("60"), new BigDecimal("7000"));

            return null;
        });

        // Execute: Compute metrics
        AssetMetricsDto metrics = assetAnalyticsService.computeMetrics("Gold", 1L);

        // Verify: Total quantity = 150 - 60 = 90
        assertEquals(0, new BigDecimal("90").compareTo(metrics.getTotalQuantity()));

        // Verify: Average buy price = 800000 / 150 = 5333.3333
        assertEquals(0, new BigDecimal("5333.3333").compareTo(metrics.getAverageBuyPrice()));

        // Verify: Sold amount = 420000
        assertEquals(0, new BigDecimal("420000").compareTo(metrics.getSoldAmount()));

        // Verify: Realized PnL = 420000 - (60 × 5333.3333) = 420000 - 319999.998 = 100000.002
        // Allowing small rounding difference
        BigDecimal expectedPnL = new BigDecimal("100000");
        assertTrue(metrics.getRealizedPnL().subtract(expectedPnL).abs().compareTo(new BigDecimal("1")) < 0,
                "Expected PnL around 100000, got: " + metrics.getRealizedPnL());
    }

    /**
     * TEST CASE 3: SELL without BUY → Handled Safely
     * 
     * Edge case: Asset container exists but has SELL transactions without BUY.
     * (This shouldn't normally happen, but we should handle it gracefully)
     * 
     * EXPECTED:
     * - averageBuyPrice = null (no BUY transactions)
     * - realizedPnL = null (cannot compute without average price)
     * - totalQuantity = negative (indicates sold more than owned)
     */
    @Test
    void sellWithoutBuy_handledSafely() {
        
        // Setup: Create asset container with only SELL transaction
        transactionTemplate.execute(status -> {
            
            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("Mystery");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(BigDecimal.ZERO);
            assetContainer.setUnit("units");
            assetContainer.setOpenedAt(Instant.now());
            assetContainer = containerRepository.save(assetContainer);

            // SELL: 10 units at 100 = 1000 (no prior BUY)
            createSellTransaction(assetContainer, new BigDecimal("10"), new BigDecimal("100"));

            return null;
        });

        // Execute: Compute metrics
        AssetMetricsDto metrics = assetAnalyticsService.computeMetrics("Mystery", 1L);

        // Verify: averageBuyPrice is null (no BUY transactions)
        assertNull(metrics.getAverageBuyPrice());

        // Verify: realizedPnL is null (cannot compute without average price)
        assertNull(metrics.getRealizedPnL());

        // Verify: totalQuantity = -10 (sold without buying)
        assertEquals(0, new BigDecimal("-10").compareTo(metrics.getTotalQuantity()));

        // Verify: soldAmount = 1000
        assertEquals(0, new BigDecimal("1000").compareTo(metrics.getSoldAmount()));

        // Verify: investedAmount = 0
        assertEquals(0, BigDecimal.ZERO.compareTo(metrics.getInvestedAmount()));
    }

    /**
     * TEST CASE 4: No Side Effects (Read-Only)
     * 
     * Verify that computing metrics does NOT modify containers or transactions.
     * 
     * EXPECTED:
     * - Container count remains the same
     * - Transaction count remains the same
     * - Container values remain unchanged
     */
    @Test
    void computeMetrics_noSideEffects() {
        
        // Setup: Create asset container and transactions
        transactionTemplate.execute(status -> {
            
            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("SBI");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(new BigDecimal("100"));
            assetContainer.setUnit("units");
            assetContainer.setOpenedAt(Instant.now());
            assetContainer = containerRepository.save(assetContainer);

            createBuyTransaction(assetContainer, new BigDecimal("50"), new BigDecimal("100"));
            createBuyTransaction(assetContainer, new BigDecimal("50"), new BigDecimal("120"));

            return null;
        });

        // Capture initial state
        long initialContainerCount = containerRepository.count();
        long initialTransactionCount = transactionRepository.count();
        BigDecimal initialContainerValue = containerRepository.findAll().get(0).getCurrentValue();

        // Execute: Compute metrics
        assetAnalyticsService.computeMetrics("SBI", 1L);

        // Verify: No new containers created
        assertEquals(initialContainerCount, containerRepository.count());

        // Verify: No new transactions created
        assertEquals(initialTransactionCount, transactionRepository.count());

        // Verify: Container value unchanged
        BigDecimal currentContainerValue = containerRepository.findAll().get(0).getCurrentValue();
        assertEquals(0, initialContainerValue.compareTo(currentContainerValue));
    }

    /**
     * TEST CASE 5: Asset Not Found → Exception
     * 
     * EXPECTED:
     * - IllegalArgumentException thrown
     * - Error message indicates asset not found
     */
    @Test
    void assetNotFound_throwsException() {
        
        // Execute and verify: Should throw exception
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            assetAnalyticsService.computeMetrics("NonExistent", 1L);
        });

        assertTrue(exception.getMessage().contains("Asset container not found"));
    }

    /**
     * TEST CASE 6: Invalid Input → Exception
     * 
     * EXPECTED:
     * - IllegalArgumentException for null/empty identifier
     * - IllegalArgumentException for null userId
     */
    @Test
    void invalidInput_throwsException() {
        
        // Null asset identifier
        assertThrows(IllegalArgumentException.class, () -> {
            assetAnalyticsService.computeMetrics(null, 1L);
        });

        // Empty asset identifier
        assertThrows(IllegalArgumentException.class, () -> {
            assetAnalyticsService.computeMetrics("", 1L);
        });

        // Blank asset identifier
        assertThrows(IllegalArgumentException.class, () -> {
            assetAnalyticsService.computeMetrics("   ", 1L);
        });

        // Null user ID
        assertThrows(IllegalArgumentException.class, () -> {
            assetAnalyticsService.computeMetrics("ITC", null);
        });
    }

    /**
     * TEST CASE 7: Only BUY Transactions → No PnL
     * 
     * EXPECTED:
     * - realizedPnL = null
     * - soldAmount = 0
     * - All other metrics computed correctly
     */
    @Test
    void onlyBuyTransactions_noPnL() {
        
        // Setup: Create asset container with only BUY transactions
        transactionTemplate.execute(status -> {
            
            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("Tesla");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(BigDecimal.ZERO);
            assetContainer.setUnit("shares");
            assetContainer.setOpenedAt(Instant.now());
            assetContainer = containerRepository.save(assetContainer);

            createBuyTransaction(assetContainer, new BigDecimal("5"), new BigDecimal("1000"));

            return null;
        });

        // Execute: Compute metrics
        AssetMetricsDto metrics = assetAnalyticsService.computeMetrics("Tesla", 1L);

        // Verify: realizedPnL is null
        assertNull(metrics.getRealizedPnL());

        // Verify: soldAmount = 0
        assertEquals(0, BigDecimal.ZERO.compareTo(metrics.getSoldAmount()));

        // Verify: Other metrics are correct
        assertEquals(0, new BigDecimal("5").compareTo(metrics.getTotalQuantity()));
        assertEquals(0, new BigDecimal("1000").compareTo(metrics.getAverageBuyPrice()));
        assertEquals(0, new BigDecimal("5000").compareTo(metrics.getInvestedAmount()));
    }

    /**
     * TEST CASE 8: Complete Sell-off → Zero Quantity
     * 
     * User buys and then sells all assets.
     * 
     * EXPECTED:
     * - totalQuantity = 0
     * - realizedPnL computed correctly
     * - averageBuyPrice still available
     */
    @Test
    void completeSellOff_zeroQuantity() {
        
        // Setup: Create asset container and transactions
        transactionTemplate.execute(status -> {
            
            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("Bitcoin");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(BigDecimal.ZERO);
            assetContainer.setUnit("BTC");
            assetContainer.setOpenedAt(Instant.now());
            assetContainer = containerRepository.save(assetContainer);

            // BUY: 2 BTC at 50000 = 100000
            createBuyTransaction(assetContainer, new BigDecimal("2"), new BigDecimal("50000"));

            // SELL: 2 BTC at 60000 = 120000
            createSellTransaction(assetContainer, new BigDecimal("2"), new BigDecimal("60000"));

            return null;
        });

        // Execute: Compute metrics
        AssetMetricsDto metrics = assetAnalyticsService.computeMetrics("Bitcoin", 1L);

        // Verify: totalQuantity = 0
        assertEquals(0, BigDecimal.ZERO.compareTo(metrics.getTotalQuantity()));

        // Verify: averageBuyPrice = 50000
        assertEquals(0, new BigDecimal("50000").compareTo(metrics.getAverageBuyPrice()));

        // Verify: realizedPnL = 120000 - (2 × 50000) = 20000
        assertEquals(0, new BigDecimal("20000").compareTo(metrics.getRealizedPnL()));
    }

    // ===========================
    // Helper Methods
    // ===========================

    private void createBuyTransaction(StateContainerEntity assetContainer, BigDecimal quantity, BigDecimal pricePerUnit) {
        StateChangeEntity tx = new StateChangeEntity();
        tx.setUserId("1");
        tx.setTransactionType(StateChangeTypeEnum.ASSET_BUY);
        tx.setAmount(quantity.multiply(pricePerUnit));
        tx.setQuantity(quantity);
        tx.setUnit(assetContainer.getUnit());
        tx.setTimestamp(Instant.now());
        tx.setTargetContainerId(assetContainer.getId()); // BUY increases asset
        tx.setCompletenessLevel(CompletenessLevelEnum.FINANCIAL);
        tx.setFinanciallyApplied(true);
        
        Map<String, Object> details = new HashMap<>();
        details.put("quantity", quantity);
        details.put("price_per_unit", pricePerUnit);
        details.put("asset_identifier", assetContainer.getName());
        tx.setDetails(details);
        
        transactionRepository.save(tx);
    }

    private void createSellTransaction(StateContainerEntity assetContainer, BigDecimal quantity, BigDecimal pricePerUnit) {
        StateChangeEntity tx = new StateChangeEntity();
        tx.setUserId("1");
        tx.setTransactionType(StateChangeTypeEnum.ASSET_SELL);
        tx.setAmount(quantity.multiply(pricePerUnit));
        tx.setQuantity(quantity);
        tx.setUnit(assetContainer.getUnit());
        tx.setTimestamp(Instant.now());
        tx.setSourceContainerId(assetContainer.getId()); // SELL decreases asset
        tx.setCompletenessLevel(CompletenessLevelEnum.FINANCIAL);
        tx.setFinanciallyApplied(true);
        
        Map<String, Object> details = new HashMap<>();
        details.put("quantity", quantity);
        details.put("price_per_unit", pricePerUnit);
        details.put("asset_identifier", assetContainer.getName());
        tx.setDetails(details);
        
        transactionRepository.save(tx);
    }
}
