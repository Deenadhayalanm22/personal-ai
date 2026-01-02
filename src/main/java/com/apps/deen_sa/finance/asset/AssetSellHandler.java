package com.apps.deen_sa.finance.asset;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.core.mutation.MutationTypeEnum;
import com.apps.deen_sa.core.mutation.StateMutationService;
import com.apps.deen_sa.core.state.*;
import com.apps.deen_sa.dto.AssetSellDto;
import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.llm.impl.AssetSellClassifier;
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
 * Phase 3: Asset SELL Transaction Handler
 * 
 * Handles user asset sale transactions with full transactional history.
 * 
 * Core Principle: Record SELL transactions and update ownership state.
 * 
 * Responsibilities:
 * 1. Extract asset identifier, quantity, and price per unit
 * 2. Calculate total amount = quantity × pricePerUnit (in Java)
 * 3. Resolve ASSET container (must exist with sufficient quantity)
 * 4. Resolve target container (bank/cash where money goes)
 * 5. Create StateChangeEntity for the SELL
 * 6. Apply financial impact: DEBIT asset quantity, CREDIT target cash
 * 7. Mark as financially applied
 * 
 * STRICT RULES:
 * - Do NOT calculate profit or loss
 * - Do NOT modify or recompute average price
 * - Do NOT touch historical BUY transactions
 * - Do NOT create or delete asset containers automatically
 * - Reject sell if quantity exceeds ownership
 */
@Service
@RequiredArgsConstructor
public class AssetSellHandler implements SpeechHandler {

    private final AssetSellClassifier assetSellClassifier;
    private final StateChangeRepository transactionRepository;
    private final StateContainerRepository containerRepository;
    private final StateContainerService stateContainerService;
    private final StateMutationService stateMutationService;

    @Override
    public String intentType() {
        return "INVESTMENT_SELL";
    }

    @Override
    @Transactional
    public SpeechResult handleSpeech(String text, ConversationContext ctx) {
        
        // Extract sell transaction details using LLM
        AssetSellDto dto = assetSellClassifier.extractSell(text);
        dto.setRawText(text);

        // Validate mandatory fields
        if (!dto.isValid()) {
            return SpeechResult.invalid(
                    dto.getReason() != null 
                            ? dto.getReason() 
                            : "Could not extract sell transaction details. Please provide asset name, quantity, and price."
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

        // Resolve ASSET container (must exist)
        Long userId = 1L; // TODO: replace with auth user
        StateContainerEntity assetContainer = resolveAssetContainer(dto, userId);

        if (assetContainer == null) {
            return SpeechResult.invalid(
                    "Could not find asset container for " + dto.getAssetIdentifier() + ". You don't own this asset."
            );
        }

        // Check if sufficient quantity is owned
        if (assetContainer.getCurrentValue().compareTo(dto.getQuantity()) < 0) {
            return SpeechResult.invalid(
                    String.format("Insufficient quantity. You own %s %s but trying to sell %s %s.",
                            assetContainer.getCurrentValue(), assetContainer.getUnit(),
                            dto.getQuantity(), dto.getUnit())
            );
        }

        // Resolve target container (where money goes)
        StateContainerEntity targetContainer = resolveTargetContainer(dto, userId);

        if (targetContainer == null) {
            return SpeechResult.invalid(
                    "Could not find target account to deposit proceeds. Please set up your bank account or cash first."
            );
        }

        // Create transaction entity
        StateChangeEntity transaction = createTransactionEntity(dto, totalAmount, userId, assetContainer, targetContainer);

        // Save transaction
        StateChangeEntity saved = transactionRepository.save(transaction);

        // Apply financial impact
        applyFinancialImpact(saved, totalAmount, assetContainer, targetContainer, dto.getQuantity());

        // Mark as financially applied
        saved.setFinanciallyApplied(true);
        transactionRepository.save(saved);

        // Reset conversation context
        ctx.reset();

        return SpeechResult.saved(saved);
    }

    @Override
    public SpeechResult handleFollowup(String userAnswer, ConversationContext ctx) {
        // For Phase 3, we don't require follow-ups
        return SpeechResult.info("Asset sell transaction has been recorded.");
    }

    /**
     * Resolve ASSET container (must exist).
     * Returns null if asset is not found.
     */
    private StateContainerEntity resolveAssetContainer(AssetSellDto dto, Long userId) {
        
        // Find existing asset container for this user
        Optional<StateContainerEntity> existing = containerRepository
                .findAll()
                .stream()
                .filter(c -> "ASSET".equals(c.getContainerType()) 
                        && dto.getAssetIdentifier().equalsIgnoreCase(c.getName())
                        && c.getOwnerId().equals(userId))
                .findFirst();

        return existing.orElse(null);
    }

    /**
     * Resolve target container (where money goes).
     * Defaults to BANK_ACCOUNT if not specified.
     */
    private StateContainerEntity resolveTargetContainer(AssetSellDto dto, Long userId) {
        List<StateContainerEntity> containers = stateContainerService.getActiveContainers(userId);

        String targetType = dto.getTargetAccount() != null ? dto.getTargetAccount() : "BANK_ACCOUNT";

        // Find matching container by type
        List<StateContainerEntity> matching = containers.stream()
                .filter(c -> c.getContainerType().equals(targetType))
                .toList();

        // Return first matching container (or null if none found)
        return matching.isEmpty() ? null : matching.getFirst();
    }

    /**
     * Create StateChangeEntity representing the SELL transaction.
     */
    private StateChangeEntity createTransactionEntity(
            AssetSellDto dto,
            BigDecimal totalAmount,
            Long userId,
            StateContainerEntity assetContainer,
            StateContainerEntity targetContainer) {

        StateChangeEntity transaction = new StateChangeEntity();
        
        transaction.setUserId(userId.toString());
        transaction.setTransactionType(StateChangeTypeEnum.ASSET_SELL);
        transaction.setAmount(totalAmount);
        transaction.setQuantity(dto.getQuantity());
        transaction.setUnit(dto.getUnit());
        
        // Set timestamp
        LocalDate tradeDate = dto.getTradeDate() != null ? dto.getTradeDate() : LocalDate.now();
        transaction.setTimestamp(tradeDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        
        transaction.setRawText(dto.getRawText());
        transaction.setSourceContainerId(assetContainer.getId());
        transaction.setTargetContainerId(targetContainer.getId());
        
        transaction.setCompletenessLevel(CompletenessLevelEnum.FINANCIAL);
        transaction.setFinanciallyApplied(false);
        transaction.setNeedsEnrichment(false);
        
        // Store sell details in details field
        Map<String, Object> details = new HashMap<>();
        details.put("quantity", dto.getQuantity());
        details.put("price_per_unit", dto.getPricePerUnit());
        details.put("trade_date", tradeDate.toString());
        details.put("asset_identifier", dto.getAssetIdentifier());
        transaction.setDetails(details);

        return transaction;
    }

    /**
     * Apply financial impact of the SELL transaction.
     * 
     * 1. DEBIT ASSET container by quantity (reduce quantity owned)
     * 2. CREDIT target container by totalAmount (money received)
     */
    private void applyFinancialImpact(
            StateChangeEntity transaction,
            BigDecimal totalAmount,
            StateContainerEntity assetContainer,
            StateContainerEntity targetContainer,
            BigDecimal quantity) {

        // 1. DEBIT ASSET container (quantity leaves)
        StateMutationCommand debitCommand = new StateMutationCommand(
                quantity, // Use quantity, not totalAmount
                MutationTypeEnum.DEBIT,
                "ASSET_SELL",
                transaction.getId(),
                transaction.getTimestamp()
        );
        stateMutationService.apply(assetContainer, debitCommand);

        // 2. CREDIT target container (money comes in)
        StateMutationCommand creditCommand = new StateMutationCommand(
                totalAmount,
                MutationTypeEnum.CREDIT,
                "ASSET_SELL",
                transaction.getId(),
                transaction.getTimestamp()
        );
        stateMutationService.apply(targetContainer, creditCommand);
    }
}
