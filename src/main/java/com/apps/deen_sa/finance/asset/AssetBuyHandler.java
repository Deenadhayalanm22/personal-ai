package com.apps.deen_sa.finance.asset;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.core.mutation.MutationTypeEnum;
import com.apps.deen_sa.core.mutation.StateMutationService;
import com.apps.deen_sa.core.state.*;
import com.apps.deen_sa.dto.AssetBuyDto;
import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.llm.impl.AssetBuyClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 2: Asset BUY Transaction Handler
 * 
 * Handles user asset purchase transactions with full transactional history.
 * 
 * Core Principle: Record BUY transactions and update ownership state.
 * 
 * Responsibilities:
 * 1. Extract asset identifier, quantity, and price per unit
 * 2. Calculate total amount = quantity × pricePerUnit (in Java)
 * 3. Resolve source container (bank/cash)
 * 4. Resolve or create ASSET container
 * 5. Create StateChangeEntity for the BUY
 * 6. Apply financial impact: DEBIT source, increase asset quantity
 * 7. Mark as financially applied
 * 
 * Works even if asset was never set up explicitly before.
 */
@Service
@RequiredArgsConstructor
public class AssetBuyHandler implements SpeechHandler {

    private final AssetBuyClassifier assetBuyClassifier;
    private final StateChangeRepository transactionRepository;
    private final StateContainerRepository containerRepository;
    private final StateContainerService stateContainerService;
    private final StateMutationService stateMutationService;

    @Override
    public String intentType() {
        return "INVESTMENT";
    }

    @Override
    @Transactional
    public SpeechResult handleSpeech(String text, ConversationContext ctx) {
        
        // Extract buy transaction details using LLM
        AssetBuyDto dto = assetBuyClassifier.extractBuy(text);
        dto.setRawText(text);

        // Validate mandatory fields
        if (!dto.isValid()) {
            return SpeechResult.invalid(
                    dto.getReason() != null 
                            ? dto.getReason() 
                            : "Could not extract buy transaction details. Please provide asset name, quantity, and price."
            );
        }

        // Check if mandatory fields are present
        if (dto.getAssetIdentifier() == null || dto.getQuantity() == null || dto.getPricePerUnit() == null) {
            return SpeechResult.invalid("Asset name, quantity, and price per unit are required.");
        }

        // Validate quantities are positive
        if (dto.getQuantity().signum() <= 0) {
            return SpeechResult.invalid("Quantity must be greater than zero.");
        }
        
        if (dto.getPricePerUnit().signum() <= 0) {
            return SpeechResult.invalid("Price per unit must be greater than zero.");
        }

        // Calculate total amount in Java (NOT in LLM)
        BigDecimal totalAmount = dto.getQuantity().multiply(dto.getPricePerUnit());

        // Resolve source container (where money comes from)
        Long userId = 1L; // TODO: replace with auth user
        StateContainerEntity sourceContainer = resolveSourceContainer(dto, userId);

        if (sourceContainer == null) {
            return SpeechResult.invalid(
                    "Could not find source account to pay from. Please set up your bank account or cash first."
            );
        }

        // Resolve or create ASSET container
        StateContainerEntity assetContainer = resolveOrCreateAssetContainer(dto, userId);

        // Create transaction entity
        StateChangeEntity transaction = createTransactionEntity(dto, totalAmount, userId, sourceContainer, assetContainer);

        // Save transaction
        StateChangeEntity saved = transactionRepository.save(transaction);

        // Apply financial impact
        applyFinancialImpact(saved, totalAmount, sourceContainer, assetContainer, dto.getQuantity());

        // Mark as financially applied
        saved.setFinanciallyApplied(true);
        transactionRepository.save(saved);

        // Reset conversation context
        ctx.reset();

        return SpeechResult.saved(saved);
    }

    @Override
    public SpeechResult handleFollowup(String userAnswer, ConversationContext ctx) {
        // For Phase 2, we don't require follow-ups
        return SpeechResult.info("Asset buy transaction has been recorded.");
    }

    /**
     * Resolve source container (where money comes from).
     * Defaults to BANK_ACCOUNT if not specified.
     */
    private StateContainerEntity resolveSourceContainer(AssetBuyDto dto, Long userId) {
        List<StateContainerEntity> containers = stateContainerService.getActiveContainers(userId);

        String sourceType = dto.getSourceAccount() != null ? dto.getSourceAccount() : "BANK_ACCOUNT";

        // Find matching container by type
        List<StateContainerEntity> matching = containers.stream()
                .filter(c -> c.getContainerType().equals(sourceType))
                .toList();

        // Return first matching container (or null if none found)
        return matching.isEmpty() ? null : matching.get(0);
    }

    /**
     * Resolve ASSET container or create it if it doesn't exist.
     * 
     * Critical: Do NOT create multiple ASSET containers for the same asset.
     */
    private StateContainerEntity resolveOrCreateAssetContainer(AssetBuyDto dto, Long userId) {
        
        // Check if asset container already exists for this user
        Optional<StateContainerEntity> existing = containerRepository
                .findAll()
                .stream()
                .filter(c -> "ASSET".equals(c.getContainerType()) 
                        && dto.getAssetIdentifier().equalsIgnoreCase(c.getName())
                        && c.getOwnerId().equals(userId))
                .findFirst();

        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new ASSET container implicitly
        StateContainerEntity container = new StateContainerEntity();
        container.setOwnerType("USER");
        container.setOwnerId(userId);
        container.setContainerType("ASSET");
        container.setName(dto.getAssetIdentifier());
        container.setStatus("ACTIVE");
        container.setCurrentValue(BigDecimal.ZERO); // Start with 0, will be updated by mutation
        container.setUnit(dto.getUnit());
        container.setOpenedAt(Instant.now());

        return containerRepository.save(container);
    }

    /**
     * Create StateChangeEntity representing the BUY transaction.
     */
    private StateChangeEntity createTransactionEntity(
            AssetBuyDto dto,
            BigDecimal totalAmount,
            Long userId,
            StateContainerEntity sourceContainer,
            StateContainerEntity assetContainer) {

        StateChangeEntity transaction = new StateChangeEntity();
        
        transaction.setUserId(userId.toString());
        transaction.setTransactionType(StateChangeTypeEnum.ASSET_BUY);
        transaction.setAmount(totalAmount);
        transaction.setQuantity(dto.getQuantity());
        transaction.setUnit(dto.getUnit());
        
        // Set timestamp
        LocalDate tradeDate = dto.getTradeDate() != null ? dto.getTradeDate() : LocalDate.now();
        transaction.setTimestamp(tradeDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        
        transaction.setRawText(dto.getRawText());
        transaction.setSourceContainerId(sourceContainer.getId());
        transaction.setTargetContainerId(assetContainer.getId());
        
        transaction.setCompletenessLevel(CompletenessLevelEnum.FINANCIAL);
        transaction.setFinanciallyApplied(false);
        transaction.setNeedsEnrichment(false);
        
        // Store buy details in details field
        Map<String, Object> details = new HashMap<>();
        details.put("quantity", dto.getQuantity());
        details.put("price_per_unit", dto.getPricePerUnit());
        details.put("trade_date", tradeDate.toString());
        details.put("asset_identifier", dto.getAssetIdentifier());
        transaction.setDetails(details);

        return transaction;
    }

    /**
     * Apply financial impact of the BUY transaction.
     * 
     * 1. DEBIT source container by totalAmount
     * 2. Increase ASSET container currentValue by quantity
     */
    private void applyFinancialImpact(
            StateChangeEntity transaction,
            BigDecimal totalAmount,
            StateContainerEntity sourceContainer,
            StateContainerEntity assetContainer,
            BigDecimal quantity) {

        // 1. DEBIT source container (money leaves bank/cash)
        StateMutationCommand debitCommand = new StateMutationCommand(
                totalAmount,
                MutationTypeEnum.DEBIT,
                "ASSET_BUY",
                transaction.getId(),
                transaction.getTimestamp()
        );
        stateMutationService.apply(sourceContainer, debitCommand);

        // 2. Increase ASSET container quantity (quantity increases)
        StateMutationCommand creditCommand = new StateMutationCommand(
                quantity, // Use quantity, not totalAmount
                MutationTypeEnum.CREDIT,
                "ASSET_BUY",
                transaction.getId(),
                transaction.getTimestamp()
        );
        stateMutationService.apply(assetContainer, creditCommand);
    }
}
