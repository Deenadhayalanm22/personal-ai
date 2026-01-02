package com.apps.deen_sa.finance.asset;

import com.apps.deen_sa.IntegrationTestBase;
import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.core.state.StateChangeEntity;
import com.apps.deen_sa.core.state.StateChangeRepository;
import com.apps.deen_sa.core.state.StateChangeTypeEnum;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.state.StateContainerRepository;
import com.apps.deen_sa.simulation.LLMTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Asset SELL Transaction Handler (Phase 3)
 * 
 * Tests verify:
 * - User can sell owned assets with identifier, quantity, and price
 * - Asset container quantity is reduced correctly
 * - Bank balance is credited correctly
 * - Transaction record is created with type ASSET_SELL
 * - Selling more than owned quantity fails safely
 * - Selling full quantity results in zero quantity
 */
@Import(LLMTestConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AssetSellHandlerIT extends IntegrationTestBase {

    @Autowired
    AssetSellHandler assetSellHandler;

    @Autowired
    StateContainerRepository containerRepository;

    @Autowired
    StateChangeRepository transactionRepository;
    
    @Autowired
    com.apps.deen_sa.core.mutation.StateMutationRepository mutationRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanupTestData() {
        transactionTemplate.execute(status -> {
            mutationRepository.deleteAll(); // Delete mutations first
            transactionRepository.deleteAll();
            containerRepository.deleteAll();
            return null;
        });
    }

    /**
     * TEST CASE 1: Sell part of owned quantity
     * 
     * User owns 100 ITC shares and sells 15.
     * 
     * EXPECTED:
     * - Asset container quantity reduced by 15 (100 → 85)
     * - Bank balance increased by totalAmount (15 × 400 = 6,000)
     * - One ASSET_SELL transaction created
     */
    @Test
    void sellAsset_partialQuantity_reducesOwnershipCorrectly() {
        
        // Setup: Create bank account and asset container with 100 shares
        transactionTemplate.execute(status -> {
            StateContainerEntity bankAccount = new StateContainerEntity();
            bankAccount.setOwnerType("USER");
            bankAccount.setOwnerId(1L);
            bankAccount.setContainerType("BANK_ACCOUNT");
            bankAccount.setName("My Bank");
            bankAccount.setStatus("ACTIVE");
            bankAccount.setCurrency("INR");
            bankAccount.setCurrentValue(new BigDecimal("50000"));
            bankAccount.setOpenedAt(Instant.now());
            containerRepository.save(bankAccount);

            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("ITC");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(new BigDecimal("100")); // Owns 100 shares
            assetContainer.setUnit("shares");
            assetContainer.setOpenedAt(Instant.now());
            containerRepository.save(assetContainer);

            return null;
        });

        // Execute: User sells "15 ITC shares at 400"
        ConversationContext ctx = new ConversationContext();
        SpeechResult result = assetSellHandler.handleSpeech(
                "I sold 15 ITC shares at 400",
                ctx
        );

        // Verify: Result is SAVED
        assertEquals(SpeechStatus.SAVED, result.getStatus());
        assertNotNull(result.getSavedEntity());

        // Verify: Two containers exist (bank + asset)
        List<StateContainerEntity> containers = containerRepository.findAll();
        assertEquals(2, containers.size());

        // Verify: ASSET container quantity reduced by 15 (100 - 15 = 85)
        StateContainerEntity assetContainer = containers.stream()
                .filter(c -> c.getContainerType().equals("ASSET"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ASSET container not found"));

        assertEquals("ITC", assetContainer.getName());
        assertEquals(0, new BigDecimal("85").compareTo(assetContainer.getCurrentValue()));
        assertEquals("shares", assetContainer.getUnit());

        // Verify: Bank balance increased by totalAmount (15 × 400 = 6,000)
        StateContainerEntity bankAccount = containers.stream()
                .filter(c -> c.getContainerType().equals("BANK_ACCOUNT"))
                .findFirst()
                .orElseThrow();

        BigDecimal expectedBalance = new BigDecimal("50000").add(new BigDecimal("6000"));
        assertEquals(0, expectedBalance.compareTo(bankAccount.getCurrentValue()));

        // Verify: One ASSET_SELL transaction created
        List<StateChangeEntity> transactions = transactionRepository.findAll();
        assertEquals(1, transactions.size());

        StateChangeEntity tx = transactions.get(0);
        assertEquals(StateChangeTypeEnum.ASSET_SELL, tx.getTransactionType());
        assertEquals(0, new BigDecimal("6000").compareTo(tx.getAmount()));
        assertEquals(0, new BigDecimal("15").compareTo(tx.getQuantity()));
        assertTrue(tx.isFinanciallyApplied());
    }

    /**
     * TEST CASE 2: Sell full quantity (zero remaining)
     * 
     * User owns 20 shares and sells all 20.
     * 
     * EXPECTED:
     * - Asset container quantity becomes 0
     * - Bank balance increases correctly
     * - Transaction created successfully
     */
    @Test
    void sellAsset_fullQuantity_resultsInZero() {
        
        // Setup: Create bank account and asset container with 20 shares
        transactionTemplate.execute(status -> {
            StateContainerEntity bankAccount = new StateContainerEntity();
            bankAccount.setOwnerType("USER");
            bankAccount.setOwnerId(1L);
            bankAccount.setContainerType("BANK_ACCOUNT");
            bankAccount.setName("My Bank");
            bankAccount.setStatus("ACTIVE");
            bankAccount.setCurrency("INR");
            bankAccount.setCurrentValue(new BigDecimal("50000"));
            bankAccount.setOpenedAt(Instant.now());
            containerRepository.save(bankAccount);

            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("ITC");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(new BigDecimal("20")); // Owns 20 shares
            assetContainer.setUnit("shares");
            assetContainer.setOpenedAt(Instant.now());
            containerRepository.save(assetContainer);

            return null;
        });

        // Execute: User sells all 20 shares at 450
        SpeechResult result = assetSellHandler.handleSpeech(
                "I sold 20 ITC shares at 450",
                new ConversationContext()
        );

        // Verify: Result is SAVED
        assertEquals(SpeechStatus.SAVED, result.getStatus());

        // Verify: ASSET container has zero quantity
        StateContainerEntity assetContainer = containerRepository.findAll().stream()
                .filter(c -> c.getContainerType().equals("ASSET"))
                .findFirst()
                .orElseThrow();

        assertEquals(0, BigDecimal.ZERO.compareTo(assetContainer.getCurrentValue()));

        // Verify: Bank balance increased by totalAmount (20 × 450 = 9,000)
        StateContainerEntity bankAccount = containerRepository.findAll().stream()
                .filter(c -> c.getContainerType().equals("BANK_ACCOUNT"))
                .findFirst()
                .orElseThrow();

        BigDecimal expectedBalance = new BigDecimal("50000").add(new BigDecimal("9000"));
        assertEquals(0, expectedBalance.compareTo(bankAccount.getCurrentValue()));
    }

    /**
     * TEST CASE 3: Sell mutual fund units
     * 
     * Different asset type (mutual fund) to verify handler works for various assets.
     * 
     * EXPECTED:
     * - Quantity reduced correctly
     * - Bank balance increases correctly
     */
    @Test
    void sellMutualFund_reducesQuantityCorrectly() {
        
        // Setup: Create bank account and mutual fund container
        transactionTemplate.execute(status -> {
            StateContainerEntity bankAccount = new StateContainerEntity();
            bankAccount.setOwnerType("USER");
            bankAccount.setOwnerId(1L);
            bankAccount.setContainerType("BANK_ACCOUNT");
            bankAccount.setName("My Bank");
            bankAccount.setStatus("ACTIVE");
            bankAccount.setCurrency("INR");
            bankAccount.setCurrentValue(new BigDecimal("50000"));
            bankAccount.setOpenedAt(Instant.now());
            containerRepository.save(bankAccount);

            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("SBI Bluechip");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(new BigDecimal("50")); // Owns 50 units
            assetContainer.setUnit("units");
            assetContainer.setOpenedAt(Instant.now());
            containerRepository.save(assetContainer);

            return null;
        });

        // Execute: Sell "5 units of SBI Bluechip at 520"
        SpeechResult result = assetSellHandler.handleSpeech(
                "Sold 5 units of SBI Bluechip at 520",
                new ConversationContext()
        );

        // Verify: Result is SAVED
        assertEquals(SpeechStatus.SAVED, result.getStatus());

        // Verify: ASSET container quantity reduced (50 - 5 = 45)
        StateContainerEntity assetContainer = containerRepository.findAll().stream()
                .filter(c -> c.getContainerType().equals("ASSET"))
                .findFirst()
                .orElseThrow();

        assertEquals(0, new BigDecimal("45").compareTo(assetContainer.getCurrentValue()));
        assertEquals("units", assetContainer.getUnit());

        // Verify: Total amount calculated correctly (5 × 520 = 2,600)
        StateChangeEntity tx = transactionRepository.findAll().get(0);
        assertEquals(0, new BigDecimal("2600").compareTo(tx.getAmount()));
    }

    /**
     * TEST CASE 4: Sell gold (physical asset)
     * 
     * EXPECTED:
     * - Gold quantity reduced correctly
     * - Bank balance increases correctly
     */
    @Test
    void sellGold_reducesQuantityCorrectly() {
        
        // Setup: Create bank account and gold container
        transactionTemplate.execute(status -> {
            StateContainerEntity bankAccount = new StateContainerEntity();
            bankAccount.setOwnerType("USER");
            bankAccount.setOwnerId(1L);
            bankAccount.setContainerType("BANK_ACCOUNT");
            bankAccount.setName("My Bank");
            bankAccount.setStatus("ACTIVE");
            bankAccount.setCurrency("INR");
            bankAccount.setCurrentValue(new BigDecimal("100000"));
            bankAccount.setOpenedAt(Instant.now());
            containerRepository.save(bankAccount);

            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("gold");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(new BigDecimal("10")); // Owns 10 grams
            assetContainer.setUnit("grams");
            assetContainer.setOpenedAt(Instant.now());
            containerRepository.save(assetContainer);

            return null;
        });

        // Execute: Sell "2 grams of gold at 6300"
        SpeechResult result = assetSellHandler.handleSpeech(
                "I sold 2 grams of gold at 6300",
                new ConversationContext()
        );

        // Verify: Result is SAVED
        assertEquals(SpeechStatus.SAVED, result.getStatus());

        // Verify: Gold quantity reduced (10 - 2 = 8)
        StateContainerEntity assetContainer = containerRepository.findAll().stream()
                .filter(c -> c.getContainerType().equals("ASSET"))
                .findFirst()
                .orElseThrow();

        assertEquals("gold", assetContainer.getName().toLowerCase());
        assertEquals(0, new BigDecimal("8").compareTo(assetContainer.getCurrentValue()));
        assertEquals("grams", assetContainer.getUnit());

        // Verify: Total amount calculated correctly (2 × 6300 = 12,600)
        StateChangeEntity tx = transactionRepository.findAll().get(0);
        assertEquals(0, new BigDecimal("12600").compareTo(tx.getAmount()));
    }

    /**
     * TEST CASE 5: Transaction details are stored correctly
     * 
     * EXPECTED:
     * - Details field contains quantity, price_per_unit, trade_date
     * - Amounts are calculated correctly in Java, not LLM
     */
    @Test
    void sellAsset_transactionDetailsStored() {
        
        // Setup: Create bank account and asset container
        transactionTemplate.execute(status -> {
            StateContainerEntity bankAccount = new StateContainerEntity();
            bankAccount.setOwnerType("USER");
            bankAccount.setOwnerId(1L);
            bankAccount.setContainerType("BANK_ACCOUNT");
            bankAccount.setName("My Bank");
            bankAccount.setStatus("ACTIVE");
            bankAccount.setCurrency("INR");
            bankAccount.setCurrentValue(new BigDecimal("50000"));
            bankAccount.setOpenedAt(Instant.now());
            containerRepository.save(bankAccount);

            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("ITC");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(new BigDecimal("100"));
            assetContainer.setUnit("shares");
            assetContainer.setOpenedAt(Instant.now());
            containerRepository.save(assetContainer);

            return null;
        });

        // Execute: Sell asset
        assetSellHandler.handleSpeech(
                "I sold 15 ITC shares at 400",
                new ConversationContext()
        );

        // Verify: Transaction details stored
        StateChangeEntity tx = transactionRepository.findAll().get(0);
        
        assertNotNull(tx.getDetails());
        assertTrue(tx.getDetails().containsKey("quantity"));
        assertTrue(tx.getDetails().containsKey("price_per_unit"));
        assertTrue(tx.getDetails().containsKey("trade_date"));
        assertTrue(tx.getDetails().containsKey("asset_identifier"));

        // Verify amounts match expected calculation
        assertEquals(0, new BigDecimal("15").compareTo(tx.getQuantity()));
        assertEquals(0, new BigDecimal("6000").compareTo(tx.getAmount())); // 15 × 400
    }

    /**
     * TEST CASE 6: Selling more than owned quantity fails
     * 
     * User owns 50 shares but tries to sell 100.
     * 
     * EXPECTED:
     * - Transaction fails with INVALID status
     * - Error message indicates insufficient quantity
     * - No transaction created
     * - Container values unchanged
     */
    @Test
    void sellAsset_exceedsOwnership_fails() {
        
        // Setup: Create bank account and asset container with only 50 shares
        transactionTemplate.execute(status -> {
            StateContainerEntity bankAccount = new StateContainerEntity();
            bankAccount.setOwnerType("USER");
            bankAccount.setOwnerId(1L);
            bankAccount.setContainerType("BANK_ACCOUNT");
            bankAccount.setName("My Bank");
            bankAccount.setStatus("ACTIVE");
            bankAccount.setCurrency("INR");
            bankAccount.setCurrentValue(new BigDecimal("50000"));
            bankAccount.setOpenedAt(Instant.now());
            containerRepository.save(bankAccount);

            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("ITC");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(new BigDecimal("50")); // Only owns 50 shares
            assetContainer.setUnit("shares");
            assetContainer.setOpenedAt(Instant.now());
            containerRepository.save(assetContainer);

            return null;
        });

        // Execute: Try to sell 100 shares (more than owned)
        SpeechResult result = assetSellHandler.handleSpeech(
                "I sold 100 ITC shares at 400",
                new ConversationContext()
        );

        // Verify: Result is INVALID
        assertEquals(SpeechStatus.INVALID, result.getStatus());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("Insufficient quantity"));

        // Verify: No transactions created
        assertEquals(0, transactionRepository.count());

        // Verify: Container values unchanged
        StateContainerEntity assetContainer = containerRepository.findAll().stream()
                .filter(c -> c.getContainerType().equals("ASSET"))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("50").compareTo(assetContainer.getCurrentValue()));

        StateContainerEntity bankAccount = containerRepository.findAll().stream()
                .filter(c -> c.getContainerType().equals("BANK_ACCOUNT"))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("50000").compareTo(bankAccount.getCurrentValue()));
    }

    /**
     * TEST CASE 7: Selling asset that is not owned fails
     * 
     * User tries to sell asset they don't own.
     * 
     * EXPECTED:
     * - INVALID status returned
     * - Error message indicates asset not found
     */
    @Test
    void sellAsset_notOwned_returnsError() {
        
        // Setup: Create bank account only (no asset container)
        transactionTemplate.execute(status -> {
            StateContainerEntity bankAccount = new StateContainerEntity();
            bankAccount.setOwnerType("USER");
            bankAccount.setOwnerId(1L);
            bankAccount.setContainerType("BANK_ACCOUNT");
            bankAccount.setName("My Bank");
            bankAccount.setStatus("ACTIVE");
            bankAccount.setCurrency("INR");
            bankAccount.setCurrentValue(new BigDecimal("50000"));
            bankAccount.setOpenedAt(Instant.now());
            containerRepository.save(bankAccount);
            return null;
        });

        // Execute: Try to sell ITC shares (not owned)
        SpeechResult result = assetSellHandler.handleSpeech(
                "I sold 15 ITC shares at 400",
                new ConversationContext()
        );

        // Verify: Result is INVALID
        assertEquals(SpeechStatus.INVALID, result.getStatus());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("don't own"));

        // Verify: No transactions created
        assertEquals(0, transactionRepository.count());
    }

    /**
     * TEST CASE 8: Invalid input handling
     * 
     * EXPECTED:
     * - No transaction created
     * - INVALID status returned
     */
    @Test
    void sellAsset_invalidInput_noRecordsCreated() {
        
        // Setup: Create bank account and asset container
        transactionTemplate.execute(status -> {
            StateContainerEntity bankAccount = new StateContainerEntity();
            bankAccount.setOwnerType("USER");
            bankAccount.setOwnerId(1L);
            bankAccount.setContainerType("BANK_ACCOUNT");
            bankAccount.setName("My Bank");
            bankAccount.setStatus("ACTIVE");
            bankAccount.setCurrency("INR");
            bankAccount.setCurrentValue(new BigDecimal("50000"));
            bankAccount.setOpenedAt(Instant.now());
            containerRepository.save(bankAccount);

            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("ITC");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(new BigDecimal("100"));
            assetContainer.setUnit("shares");
            assetContainer.setOpenedAt(Instant.now());
            containerRepository.save(assetContainer);

            return null;
        });

        // Execute: Invalid input (no price)
        SpeechResult result = assetSellHandler.handleSpeech(
                "I sold some ITC shares",
                new ConversationContext()
        );

        // Verify: Result is INVALID
        assertEquals(SpeechStatus.INVALID, result.getStatus());

        // Verify: No transactions created
        assertEquals(0, transactionRepository.count());

        // Verify: Container values unchanged
        StateContainerEntity assetContainer = containerRepository.findAll().stream()
                .filter(c -> c.getContainerType().equals("ASSET"))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(assetContainer.getCurrentValue()));
    }

    /**
     * TEST CASE 9: No target container available
     * 
     * EXPECTED:
     * - INVALID status returned
     * - Error message indicates missing target account
     */
    @Test
    void sellAsset_noTargetContainer_returnsError() {
        
        // Setup: Create only asset container (no bank account)
        transactionTemplate.execute(status -> {
            StateContainerEntity assetContainer = new StateContainerEntity();
            assetContainer.setOwnerType("USER");
            assetContainer.setOwnerId(1L);
            assetContainer.setContainerType("ASSET");
            assetContainer.setName("ITC");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(new BigDecimal("100"));
            assetContainer.setUnit("shares");
            assetContainer.setOpenedAt(Instant.now());
            containerRepository.save(assetContainer);
            return null;
        });

        // Execute: Try to sell without target container
        SpeechResult result = assetSellHandler.handleSpeech(
                "I sold 15 ITC shares at 400",
                new ConversationContext()
        );

        // Verify: Result is INVALID
        assertEquals(SpeechStatus.INVALID, result.getStatus());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("target account"));

        // Verify: No transactions created
        assertEquals(0, transactionRepository.count());
    }
}
