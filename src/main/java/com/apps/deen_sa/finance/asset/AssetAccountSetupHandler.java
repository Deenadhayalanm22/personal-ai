package com.apps.deen_sa.finance.asset;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.state.StateContainerRepository;
import com.apps.deen_sa.dto.AssetDto;
import com.apps.deen_sa.llm.impl.AssetClassifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 1: Asset Account Setup Handler
 * 
 * Handles user declarations of existing asset ownership with minimal required information.
 * 
 * Core Principle: OWNERSHIP STATE, not monetary transactions.
 * 
 * Responsibilities:
 * 1. Extract asset identifier and quantity (mandatory)
 * 2. Immediately create/update ONE ASSET container
 * 3. Persist container
 * 4. Return confirmation with OPTIONAL follow-up questions
 * 
 * Strict Rules:
 * - Do NOT create StateChangeEntity records
 * - Do NOT touch bank/cash balances
 * - Do NOT calculate money or valuation
 * - Do NOT require broker or cost to proceed
 */
@Service
public class AssetAccountSetupHandler implements SpeechHandler {

    private final AssetClassifier assetClassifier;
    private final StateContainerRepository containerRepository;

    public AssetAccountSetupHandler(
            AssetClassifier assetClassifier,
            StateContainerRepository containerRepository) {
        this.assetClassifier = assetClassifier;
        this.containerRepository = containerRepository;
    }

    @Override
    public String intentType() {
        return "ASSET_SETUP";
    }

    @Override
    public SpeechResult handleSpeech(String text, ConversationContext ctx) {
        
        // Extract asset details using LLM
        AssetDto dto = assetClassifier.extractAsset(text);
        dto.setRawText(text);

        // Validate mandatory fields
        if (!dto.isValid()) {
            return SpeechResult.invalid(
                    dto.getReason() != null 
                            ? dto.getReason() 
                            : "Could not extract asset details. Please provide asset name and quantity."
            );
        }

        // Check if mandatory fields are present
        if (dto.getAssetIdentifier() == null || dto.getQuantity() == null) {
            return SpeechResult.invalid("Asset name and quantity are required.");
        }

        // Save the asset container immediately
        StateContainerEntity savedContainer = saveAssetContainer(dto);
        
        // Clear context since we're done with the main flow
        ctx.reset();

        // Generate OPTIONAL follow-up question for enrichment
        String followupQuestion = generateEnrichmentFollowup(dto);
        
        // Return success with optional follow-up
        String confirmationMessage = String.format(
                "Asset recorded: %s (%s %s). %s",
                dto.getAssetIdentifier(),
                dto.getQuantity(),
                dto.getUnit() != null ? dto.getUnit() : "",
                followupQuestion != null ? followupQuestion : ""
        );

        return SpeechResult.builder()
                .status(com.apps.deen_sa.conversation.SpeechStatus.SAVED)
                .message(confirmationMessage)
                .savedEntity(savedContainer)
                .needFollowup(false) // Follow-ups are optional, don't block
                .build();
    }

    @Override
    public SpeechResult handleFollowup(String userAnswer, ConversationContext ctx) {
        // For Phase 1, we don't require follow-ups
        // User may skip or ignore enrichment questions
        // If we get here, just acknowledge and return
        return SpeechResult.info("Thank you. Your asset has already been recorded.");
    }

    /**
     * Save asset as a StateContainer with type ASSET.
     * Sets currentValue = quantity to represent ownership state.
     */
    private StateContainerEntity saveAssetContainer(AssetDto dto) {
        
        // Check if asset already exists for this user
        Optional<StateContainerEntity> existing = containerRepository
                .findAll()
                .stream()
                .filter(c -> "ASSET".equals(c.getContainerType()) 
                        && dto.getAssetIdentifier().equalsIgnoreCase(c.getName())
                        && c.getOwnerId().equals(1L)) // TODO: replace with auth user
                .findFirst();

        StateContainerEntity container;
        
        if (existing.isPresent()) {
            // Update existing asset
            container = existing.get();
            container.setCurrentValue(dto.getQuantity());
            container.setUnit(dto.getUnit());
        } else {
            // Create new asset container
            container = new StateContainerEntity();
            container.setOwnerType("USER");
            container.setOwnerId(1L); // TODO: replace with auth user
            container.setContainerType("ASSET");
            container.setName(dto.getAssetIdentifier());
            container.setStatus("ACTIVE");
            container.setOpenedAt(Instant.now());
        }

        // Set quantity as currentValue
        container.setCurrentValue(dto.getQuantity());
        container.setUnit(dto.getUnit());

        // Store optional enrichment data in details
        Map<String, Object> details = new HashMap<>();
        if (dto.getBroker() != null) {
            details.put("broker", dto.getBroker());
        }
        if (dto.getInvestedAmount() != null) {
            details.put("investedAmount", dto.getInvestedAmount());
        }
        if (!details.isEmpty()) {
            container.setDetails(details);
        }

        return containerRepository.save(container);
    }

    /**
     * Generate optional follow-up question for enrichment.
     * User may skip or ignore these questions.
     */
    private String generateEnrichmentFollowup(AssetDto dto) {
        // Only suggest follow-ups if enrichment data is missing
        if (dto.getBroker() == null) {
            return assetClassifier.generateFollowupQuestion("broker");
        }
        if (dto.getInvestedAmount() == null) {
            return assetClassifier.generateFollowupQuestion("investedAmount");
        }
        return null; // No follow-up needed
    }
}
