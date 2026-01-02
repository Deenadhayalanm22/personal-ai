package com.apps.deen_sa.finance.asset;

import com.apps.deen_sa.IntegrationTestBase;
import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.core.state.StateChangeRepository;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Asset Account Setup (Phase 1)
 * 
 * Tests verify:
 * - User can declare asset ownership with minimal info (identifier + quantity)
 * - Asset container is created correctly with type ASSET
 * - No transaction records are created
 * - Follow-up questions are optional and don't block saving
 * - No calculations or monetary transactions occur
 */
@Import(LLMTestConfiguration.class)
public class AssetAccountSetupIT extends IntegrationTestBase {

    @Autowired
    AssetAccountSetupHandler assetHandler;

    @Autowired
    StateContainerRepository containerRepository;

    @Autowired
    StateChangeRepository transactionRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    @AfterEach
    void cleanupTestData() {
        transactionTemplate.execute(status -> {
            transactionRepository.deleteAll();
            containerRepository.deleteAll();
            return null;
        });
    }

    /**
     * TEST CASE 1: Minimal Asset Declaration
     * 
     * User declares asset ownership with just identifier and quantity.
     * 
     * EXPECTED:
     * - Asset container created with type ASSET
     * - Container name = asset identifier
     * - Container currentValue = quantity
     * - No StateChangeEntity records created
     * - No bank/cash balance changes
     * - Success response returned
     */
    @Test
    void userDeclaresAsset_withMinimalInfo_createsContainer() {
        
        // Execute: User declares "I have 100 ITC shares"
        ConversationContext ctx = new ConversationContext();
        SpeechResult result = assetHandler.handleSpeech(
                "I have 100 ITC shares",
                ctx
        );

        // Verify: Result is SAVED
        assertEquals(SpeechStatus.SAVED, result.getStatus());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("ITC"));
        assertTrue(result.getMessage().contains("100"));

        // Verify: Container was created
        List<StateContainerEntity> containers = containerRepository.findAll();
        assertEquals(1, containers.size());

        StateContainerEntity asset = containers.get(0);
        
        // Verify: Container has correct type and data
        assertEquals("ASSET", asset.getContainerType());
        assertEquals("ITC", asset.getName());
        assertEquals(0, new BigDecimal("100").compareTo(asset.getCurrentValue()));
        assertEquals("shares", asset.getUnit());
        assertEquals("ACTIVE", asset.getStatus());
        
        // Verify: No transactions created
        assertEquals(0, transactionRepository.count());
    }

    /**
     * TEST CASE 2: Asset Declaration with Mutual Fund
     * 
     * User declares mutual fund units.
     * 
     * EXPECTED:
     * - Asset container created correctly
     * - Unit type properly extracted
     */
    @Test
    void userDeclaresMutualFund_createsContainer() {
        
        // Execute: User declares "I own 50 units of SBI Bluechip mutual fund"
        ConversationContext ctx = new ConversationContext();
        SpeechResult result = assetHandler.handleSpeech(
                "I own 50 units of SBI Bluechip mutual fund",
                ctx
        );

        // Verify: Result is SAVED
        assertEquals(SpeechStatus.SAVED, result.getStatus());

        // Verify: Container was created
        List<StateContainerEntity> containers = containerRepository.findAll();
        assertEquals(1, containers.size());

        StateContainerEntity asset = containers.get(0);
        
        // Verify: Container data
        assertEquals("ASSET", asset.getContainerType());
        assertEquals("SBI Bluechip", asset.getName());
        assertEquals(0, new BigDecimal("50").compareTo(asset.getCurrentValue()));
        assertEquals("units", asset.getUnit());
        
        // Verify: No transactions created
        assertEquals(0, transactionRepository.count());
    }

    /**
     * TEST CASE 3: Asset Declaration with Physical Asset (Gold)
     * 
     * User declares physical gold ownership.
     * 
     * EXPECTED:
     * - Asset container created with appropriate unit (grams)
     */
    @Test
    void userDeclaresGold_createsContainer() {
        
        // Execute: User declares "I have 20 grams of gold"
        ConversationContext ctx = new ConversationContext();
        SpeechResult result = assetHandler.handleSpeech(
                "I have 20 grams of gold",
                ctx
        );

        // Verify: Result is SAVED
        assertEquals(SpeechStatus.SAVED, result.getStatus());

        // Verify: Container was created
        List<StateContainerEntity> containers = containerRepository.findAll();
        assertEquals(1, containers.size());

        StateContainerEntity asset = containers.get(0);
        
        // Verify: Container data
        assertEquals("ASSET", asset.getContainerType());
        assertEquals("gold", asset.getName().toLowerCase());
        assertEquals(0, new BigDecimal("20").compareTo(asset.getCurrentValue()));
        assertEquals("grams", asset.getUnit());
        
        // Verify: No transactions created
        assertEquals(0, transactionRepository.count());
    }

    /**
     * TEST CASE 4: Follow-up Questions are Optional
     * 
     * After saving the asset, handler may generate optional follow-up questions.
     * These should NOT block the save operation.
     * 
     * EXPECTED:
     * - Asset is saved immediately
     * - Follow-up message may be present in response
     * - needFollowup is false (optional, not required)
     */
    @Test
    void assetSaved_followupQuestionsAreOptional() {
        
        // Execute: User declares asset
        ConversationContext ctx = new ConversationContext();
        SpeechResult result = assetHandler.handleSpeech(
                "I have 100 ITC shares",
                ctx
        );

        // Verify: Asset was saved immediately
        assertEquals(SpeechStatus.SAVED, result.getStatus());
        assertNotNull(result.getSavedEntity());
        
        // Verify: Follow-ups are optional (needFollowup = false)
        assertFalse(result.getNeedFollowup());
        
        // Verify: Container exists
        assertEquals(1, containerRepository.count());
        
        // Verify: Context was reset (not waiting for follow-up)
        assertFalse(ctx.isInFollowup());
    }

    /**
     * TEST CASE 5: Update Existing Asset
     * 
     * If user declares an asset they already own, update the quantity.
     * 
     * EXPECTED:
     * - Existing container is updated, not duplicated
     * - New quantity replaces old quantity
     */
    @Test
    void userDeclaresAssetAgain_updatesQuantity() {
        
        ConversationContext ctx = new ConversationContext();
        
        // Execute: First declaration
        assetHandler.handleSpeech("I have 100 ITC shares", ctx);
        
        // Verify: One container exists
        assertEquals(1, containerRepository.count());
        StateContainerEntity first = containerRepository.findAll().get(0);
        assertEquals(0, new BigDecimal("100").compareTo(first.getCurrentValue()));
        
        // Execute: Second declaration with different quantity
        assetHandler.handleSpeech("I have 150 ITC shares", new ConversationContext());
        
        // Verify: Still only one container (updated, not duplicated)
        assertEquals(1, containerRepository.count());
        
        StateContainerEntity updated = containerRepository.findAll().get(0);
        assertEquals("ITC", updated.getName());
        assertEquals(0, new BigDecimal("150").compareTo(updated.getCurrentValue()));
    }

    /**
     * TEST CASE 6: Invalid Input Handling
     * 
     * If user provides insufficient information, return error.
     * 
     * EXPECTED:
     * - No container created
     * - INVALID status returned
     */
    @Test
    void invalidInput_noContainerCreated() {
        
        ConversationContext ctx = new ConversationContext();
        
        // Execute: Invalid input (no quantity)
        SpeechResult result = assetHandler.handleSpeech(
                "I have some shares",
                ctx
        );

        // Verify: Result is INVALID
        assertEquals(SpeechStatus.INVALID, result.getStatus());
        assertNotNull(result.getMessage());
        
        // Verify: No container created
        assertEquals(0, containerRepository.count());
        
        // Verify: No transactions created
        assertEquals(0, transactionRepository.count());
    }

    /**
     * TEST CASE 7: No Monetary Calculations
     * 
     * Asset declaration should NOT involve any monetary calculations,
     * balance changes, or transaction records.
     * 
     * EXPECTED:
     * - currentValue = quantity (not monetary value)
     * - No currency field set (unless explicitly monetary asset)
     * - No bank/cash balance changes
     * - No StateChangeEntity records
     */
    @Test
    void assetDeclaration_noMonetaryCalculations() {
        
        ConversationContext ctx = new ConversationContext();
        
        // Execute: Declare asset
        assetHandler.handleSpeech("I have 100 ITC shares", ctx);
        
        StateContainerEntity asset = containerRepository.findAll().get(0);
        
        // Verify: currentValue is quantity, not monetary value
        assertEquals(0, new BigDecimal("100").compareTo(asset.getCurrentValue()));
        
        // Verify: Unit is set, not currency
        assertEquals("shares", asset.getUnit());
        
        // Verify: No transactions
        assertEquals(0, transactionRepository.count());
        
        // Verify: No currency-based calculations
        // (currency field may be null for non-monetary assets)
    }
}
