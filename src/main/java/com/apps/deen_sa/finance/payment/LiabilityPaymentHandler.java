package com.apps.deen_sa.finance.payment;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.core.state.StateChangeEntity;
import com.apps.deen_sa.core.state.StateChangeRepository;
import com.apps.deen_sa.core.state.StateChangeTypeEnum;
import com.apps.deen_sa.core.state.CompletenessLevelEnum;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.dto.LiabilityPaymentDto;
import com.apps.deen_sa.finance.account.StateMutationService;
import com.apps.deen_sa.finance.account.StateContainerService;
import com.apps.deen_sa.finance.account.strategy.AdjustmentCommandFactory;
import com.apps.deen_sa.finance.account.strategy.CreditSettlementStrategy;
import com.apps.deen_sa.finance.account.strategy.StateMutationStrategy;
import com.apps.deen_sa.finance.account.strategy.StateMutationStrategyResolver;
import com.apps.deen_sa.llm.impl.LiabilityPaymentClassifier;
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

@Service
@RequiredArgsConstructor
public class LiabilityPaymentHandler implements SpeechHandler {

    private final LiabilityPaymentClassifier llm;
    private final StateChangeRepository transactionRepository;
    private final StateContainerService stateContainerService;
    private final StateMutationService stateMutationService;
    private final AdjustmentCommandFactory adjustmentCommandFactory;
    private final StateMutationStrategyResolver strategyResolver;

    @Override
    public String intentType() {
        return "LIABILITY_PAYMENT";
    }

    @Override
    @Transactional
    public SpeechResult handleSpeech(String text, ConversationContext ctx) {

        // Extract payment details from user input
        LiabilityPaymentDto dto = llm.extractPayment(text);
        dto.setRawText(text);

        if (!dto.isValid()) {
            return SpeechResult.invalid(
                    dto.getReason() != null ? dto.getReason() : "Could not extract payment details"
            );
        }

        // Validate essential fields
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            return SpeechResult.invalid("Payment amount must be greater than zero");
        }

        if (dto.getTargetLiability() == null) {
            return SpeechResult.invalid("Could not determine what liability to pay (credit card or loan)");
        }

        // Resolve source container (bank account or wallet)
        Long userId = 1L; // TODO: Get from authentication context
        StateContainerEntity sourceContainer = resolveSourceContainer(dto, userId);

        if (sourceContainer == null) {
            return SpeechResult.invalid(
                    "Could not find source account to pay from. Please specify your bank account or wallet."
            );
        }

        // Resolve target liability (credit card or loan)
        StateContainerEntity targetContainer = resolveTargetLiability(dto, userId);

        if (targetContainer == null) {
            return SpeechResult.invalid(
                    "Could not find the " + dto.getTargetLiability().toLowerCase() + " to pay. " +
                            "Please set up your " + dto.getTargetLiability().toLowerCase() + " first."
            );
        }

        // Create transaction entity
        StateChangeEntity transaction = createTransactionEntity(dto, userId, sourceContainer, targetContainer);

        // Save transaction
        StateChangeEntity saved = transactionRepository.save(transaction);

        // Apply financial impact
        applyFinancialImpact(saved, sourceContainer, targetContainer);

        // Mark as financially applied
        saved.setFinanciallyApplied(true);
        transactionRepository.save(saved);

        // Reset conversation context
        ctx.reset();

        return SpeechResult.saved(saved);
    }

    @Override
    public SpeechResult handleFollowup(String answer, ConversationContext ctx) {
        // Liability payments are straightforward - no follow-up needed for now
        return SpeechResult.invalid("Follow-up not supported for liability payments yet");
    }

    /**
     * Resolve source container (where money comes from).
     * Defaults to BANK_ACCOUNT if not specified.
     */
    private StateContainerEntity resolveSourceContainer(LiabilityPaymentDto dto, Long userId) {
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
     * Resolve target liability container (credit card or loan).
     */
    private StateContainerEntity resolveTargetLiability(LiabilityPaymentDto dto, Long userId) {
        List<StateContainerEntity> containers = stateContainerService.getActiveContainers(userId);

        String targetType = dto.getTargetLiability();

        // Find matching container by type
        List<StateContainerEntity> matching = containers.stream()
                .filter(c -> c.getContainerType().equals(targetType))
                .toList();

        // If target name is specified, try to match by name
        if (dto.getTargetName() != null && !matching.isEmpty()) {
            List<StateContainerEntity> namedMatches = matching.stream()
                    .filter(c -> c.getName() != null &&
                            c.getName().toLowerCase().contains(dto.getTargetName().toLowerCase()))
                    .toList();

            if (!namedMatches.isEmpty()) {
                return namedMatches.get(0);
            }
        }

        // Return first matching container by type
        return matching.isEmpty() ? null : matching.get(0);
    }

    /**
     * Create StateChangeEntity from payment DTO.
     */
    private StateChangeEntity createTransactionEntity(
            LiabilityPaymentDto dto,
            Long userId,
            StateContainerEntity source,
            StateContainerEntity target) {

        StateChangeEntity tx = new StateChangeEntity();

        tx.setUserId(String.valueOf(userId));
        tx.setTransactionType(StateChangeTypeEnum.TRANSFER);
        tx.setAmount(dto.getAmount());

        tx.setSourceContainerId(source.getId());
        tx.setTargetContainerId(target.getId());

        // Set timestamp
        if (dto.getPaymentDate() != null) {
            tx.setTimestamp(dto.getPaymentDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
        } else {
            tx.setTimestamp(Instant.now());
        }

        tx.setRawText(dto.getRawText());

        // Store payment details in details field
        Map<String, Object> details = new HashMap<>();
        details.put("reason", dto.getPaymentType());
        details.put("targetLiability", dto.getTargetLiability());
        if (dto.getTargetName() != null) {
            details.put("targetName", dto.getTargetName());
        }
        tx.setDetails(details);

        // Set completeness level
        tx.setCompletenessLevel(CompletenessLevelEnum.FINANCIAL);
        tx.setFinanciallyApplied(false);
        tx.setNeedsEnrichment(false);

        return tx;
    }

    /**
     * Apply financial impact for transfer transaction.
     * Debits source container and credits target liability.
     */
    private void applyFinancialImpact(
            StateChangeEntity tx,
            StateContainerEntity sourceContainer,
            StateContainerEntity targetContainer) {

        if (tx.isFinanciallyApplied()) {
            return; // Idempotency check
        }

        String reason = tx.getDetails() != null && tx.getDetails().get("reason") != null
                ? (String) tx.getDetails().get("reason")
                : "LIABILITY_PAYMENT";

        // 1. Debit source container (money leaves bank account)
        StateMutationCommand debitCommand = adjustmentCommandFactory.forTransferDebit(tx, reason);
        stateMutationService.apply(sourceContainer, debitCommand);

        // 2. Credit target liability (payment reduces outstanding)
        // Use the specialized payment method for credit settlement
        StateMutationStrategy strategy = strategyResolver.resolve(targetContainer);

        if (strategy instanceof CreditSettlementStrategy) {
            // Use specialized payment method that reduces outstanding
            StateMutationCommand creditCommand = adjustmentCommandFactory.forTransferCredit(tx, reason);
            
            // Create audit trail
            stateMutationService.apply(targetContainer, creditCommand);
            
            // Apply payment to reduce outstanding
            ((CreditSettlementStrategy) strategy).applyPayment(targetContainer, tx.getAmount());
            
            // Save updated container
            targetContainer.setLastActivityAt(Instant.now());
            stateContainerService.UpdateValueContainer(targetContainer);
        } else {
            // Fallback: use regular credit adjustment
            StateMutationCommand creditCommand = adjustmentCommandFactory.forTransferCredit(tx, reason);
            stateMutationService.apply(targetContainer, creditCommand);
        }
    }
}
