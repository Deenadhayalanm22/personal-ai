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
 * Integration tests for Asset BUY Transaction Handler (Phase 2)
 * 
 * Tests verify:
 * - User can buy assets with identifier, quantity, and price
 * - ASSET container is created implicitly if not exists
 * - Bank balance is debited correctly
 * - Asset quantity is increased correctly
 * - Transaction record is created
 * - Multiple BUY transactions work correctly
 * - Cumulative quantity calculation
 */
@Import(LLMTestConfiguration.class)
public class AssetBuyHandlerIT extends IntegrationTestBase {

    @Autowired
    AssetBuyHandler assetBuyHandler;

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
            mutationRepository.deleteAll(); // Delete mutations first
            transactionRepository.deleteAll();
            containerRepository.deleteAll();
            return null;
        });
    }

    /**
     * TEST CASE 1: Buy asset with no prior container (implicit creation)
     * 
     * User buys asset that has never been set up before.
     * 
     * EXPECTED:
     * - ASSET container created implicitly
     * - Bank balance reduced by totalAmount (quantity × pricePerUnit)
     * - Asset quantity equals purchase quantity
     * - One ASSET_BUY transaction created
     */
    @Test
    void buyAsset_noPriorContainer_createsContainerImplicitly() {
        
        // Setup: Create bank account with 50,000
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

        // Execute: User buys "29 ITC shares at 380"
        ConversationContext ctx = new ConversationContext();
        SpeechResult result = assetBuyHandler.handleSpeech(
                "I bought 29 ITC shares at 380",
                ctx
        );

        // Verify: Result is SAVED
        assertEquals(SpeechStatus.SAVED, result.getStatus());
        assertNotNull(result.getSavedEntity());

        // Verify: Two containers exist (bank + asset)
        List<StateContainerEntity> containers = containerRepository.findAll();
        assertEquals(2, containers.size());

        // Verify: ASSET container was created
        StateContainerEntity assetContainer = containers.stream()
                .filter(c -> c.getContainerType().equals("ASSET"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ASSET container not created"));

        assertEquals("ASSET", assetContainer.getContainerType());
        assertEquals("ITC", assetContainer.getName());
        assertEquals(0, new BigDecimal("29").compareTo(assetContainer.getCurrentValue()));
        assertEquals("shares", assetContainer.getUnit());

        // Verify: Bank balance reduced by totalAmount (29 × 380 = 11,020)
        StateContainerEntity bankAccount = containers.stream()
                .filter(c -> c.getContainerType().equals("BANK_ACCOUNT"))
                .findFirst()
                .orElseThrow();

        BigDecimal expectedBalance = new BigDecimal("50000").subtract(new BigDecimal("11020"));
        assertEquals(0, expectedBalance.compareTo(bankAccount.getCurrentValue()));

        // Verify: One ASSET_BUY transaction created
        List<StateChangeEntity> transactions = transactionRepository.findAll();
        assertEquals(1, transactions.size());

        StateChangeEntity tx = transactions.get(0);
        assertEquals(StateChangeTypeEnum.ASSET_BUY, tx.getTransactionType());
        assertEquals(0, new BigDecimal("11020").compareTo(tx.getAmount()));
        assertEquals(0, new BigDecimal("29").compareTo(tx.getQuantity()));
        assertTrue(tx.isFinanciallyApplied());
    }

    /**
     * TEST CASE 2: Buy asset with existing container (cumulative quantity)
     * 
     * User already owns the asset and buys more.
     * 
     * EXPECTED:
     * - Existing ASSET container is reused
     * - Quantity is cumulative (not replaced)
     * - Each BUY creates its own transaction
     */
    @Test
    void buyAsset_existingContainer_cumulativeQuantity() {
        
        // Setup: Create bank account and existing asset container
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
            assetContainer.setName("ITC");
            assetContainer.setStatus("ACTIVE");
            assetContainer.setCurrentValue(new BigDecimal("100")); // Already owns 100 shares
            assetContainer.setUnit("shares");
            assetContainer.setOpenedAt(Instant.now());
            containerRepository.save(assetContainer);

            return null;
        });

        // Execute: First buy - 29 shares at 380
        assetBuyHandler.handleSpeech(
                "I bought 29 ITC shares at 380",
                new ConversationContext()
        );

        // Execute: Second buy - 50 shares at 400
        assetBuyHandler.handleSpeech(
                "I bought 50 ITC shares at 400",
                new ConversationContext()
        );

        // Verify: Still only 2 containers (no duplicate ASSET container)
        List<StateContainerEntity> containers = containerRepository.findAll();
        assertEquals(2, containers.size());

        // Verify: ASSET container has cumulative quantity (100 + 29 + 50 = 179)
        StateContainerEntity assetContainer = containers.stream()
                .filter(c -> c.getContainerType().equals("ASSET"))
                .findFirst()
                .orElseThrow();

        assertEquals(0, new BigDecimal("179").compareTo(assetContainer.getCurrentValue()));

        // Verify: Bank balance reduced by both purchases
        // First: 29 × 380 = 11,020
        // Second: 50 × 400 = 20,000
        // Total deducted: 31,020
        StateContainerEntity bankAccount = containers.stream()
                .filter(c -> c.getContainerType().equals("BANK_ACCOUNT"))
                .findFirst()
                .orElseThrow();

        BigDecimal expectedBalance = new BigDecimal("100000").subtract(new BigDecimal("31020"));
        assertEquals(0, expectedBalance.compareTo(bankAccount.getCurrentValue()));

        // Verify: Two ASSET_BUY transactions created
        List<StateChangeEntity> transactions = transactionRepository.findAll();
        assertEquals(2, transactions.size());

        // All transactions should be of type ASSET_BUY
        assertTrue(transactions.stream().allMatch(tx -> 
                tx.getTransactionType() == StateChangeTypeEnum.ASSET_BUY));
    }

    /**
     * TEST CASE 3: Buy mutual fund units
     * 
     * Different asset type (mutual fund) to verify handler works for various assets.
     * 
     * EXPECTED:
     * - ASSET container created with correct identifier
     * - Unit type set correctly
     */
    @Test
    void buyMutualFund_createsCorrectContainer() {
        
        // Setup: Create bank account
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

        // Execute: Buy mutual fund "10 SBI Bluechip units at 520"
        SpeechResult result = assetBuyHandler.handleSpeech(
                "Purchased 10 SBI Bluechip mutual fund units at 520",
                new ConversationContext()
        );

        // Verify: Result is SAVED
        assertEquals(SpeechStatus.SAVED, result.getStatus());

        // Verify: ASSET container created
        StateContainerEntity assetContainer = containerRepository.findAll().stream()
                .filter(c -> c.getContainerType().equals("ASSET"))
                .findFirst()
                .orElseThrow();

        // Check that identifier contains expected keywords (mock may extract partially)
        assertTrue(assetContainer.getName().contains("SBI") || assetContainer.getName().contains("Bluechip"));
        assertEquals(0, new BigDecimal("10").compareTo(assetContainer.getCurrentValue()));
        assertEquals("units", assetContainer.getUnit());

        // Verify: Total amount calculated correctly (10 × 520 = 5,200)
        StateChangeEntity tx = transactionRepository.findAll().get(0);
        assertEquals(0, new BigDecimal("5200").compareTo(tx.getAmount()));
    }

    /**
     * TEST CASE 4: Buy gold (physical asset)
     * 
     * EXPECTED:
     * - ASSET container created for gold
     * - Unit type is grams
     */
    @Test
    void buyGold_createsCorrectContainer() {
        
        // Setup: Create bank account
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
            return null;
        });

        // Execute: Buy gold "5 grams at 6200"
        SpeechResult result = assetBuyHandler.handleSpeech(
                "Bought 5 grams of gold at 6200",
                new ConversationContext()
        );

        // Verify: Result is SAVED
        assertEquals(SpeechStatus.SAVED, result.getStatus());

        // Verify: ASSET container created for gold
        StateContainerEntity assetContainer = containerRepository.findAll().stream()
                .filter(c -> c.getContainerType().equals("ASSET"))
                .findFirst()
                .orElseThrow();

        assertEquals("gold", assetContainer.getName().toLowerCase());
        assertEquals(0, new BigDecimal("5").compareTo(assetContainer.getCurrentValue()));
        assertEquals("grams", assetContainer.getUnit());

        // Verify: Total amount calculated correctly (5 × 6200 = 31,000)
        StateChangeEntity tx = transactionRepository.findAll().get(0);
        assertEquals(0, new BigDecimal("31000").compareTo(tx.getAmount()));
    }

    /**
     * TEST CASE 5: Transaction details are stored correctly
     * 
     * EXPECTED:
     * - Details field contains quantity, price_per_unit, trade_date
     * - Amounts are calculated correctly in Java, not LLM
     */
    @Test
    void buyAsset_transactionDetailsStored() {
        
        // Setup: Create bank account
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

        // Execute: Buy asset
        assetBuyHandler.handleSpeech(
                "I bought 29 ITC shares at 380",
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
        assertEquals(0, new BigDecimal("29").compareTo(tx.getQuantity()));
        assertEquals(0, new BigDecimal("11020").compareTo(tx.getAmount())); // 29 × 380
    }

    /**
     * TEST CASE 6: Invalid input handling
     * 
     * EXPECTED:
     * - No container created
     * - No transaction created
     * - INVALID status returned
     */
    @Test
    void buyAsset_invalidInput_noRecordsCreated() {
        
        // Setup: Create bank account
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

        // Execute: Invalid input (no price)
        SpeechResult result = assetBuyHandler.handleSpeech(
                "I bought some ITC shares",
                new ConversationContext()
        );

        // Verify: Result is INVALID
        assertEquals(SpeechStatus.INVALID, result.getStatus());

        // Verify: No ASSET container created
        List<StateContainerEntity> containers = containerRepository.findAll();
        assertEquals(1, containers.size()); // Only bank account

        // Verify: No transactions created
        assertEquals(0, transactionRepository.count());
    }

    /**
     * TEST CASE 7: No source container available
     * 
     * EXPECTED:
     * - INVALID status returned
     * - Error message indicates missing source account
     */
    @Test
    void buyAsset_noSourceContainer_returnsError() {
        
        // Execute: Try to buy without any containers set up
        SpeechResult result = assetBuyHandler.handleSpeech(
                "I bought 29 ITC shares at 380",
                new ConversationContext()
        );

        // Verify: Result is INVALID
        assertEquals(SpeechStatus.INVALID, result.getStatus());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("source account"));

        // Verify: No containers or transactions created
        assertEquals(0, containerRepository.count());
        assertEquals(0, transactionRepository.count());
    }
}
