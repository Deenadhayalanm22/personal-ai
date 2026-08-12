package com.apps.deen_sa.finance.payment;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.ResponseAction;
import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum;
import com.apps.deen_sa.finance.legacy.state.CompletenessLevelEnum;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.dto.LiabilityPaymentDto;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationService;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationEntity;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationRepository;
import com.apps.deen_sa.finance.legacy.mutation.MutationTypeEnum;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
import com.apps.deen_sa.finance.account.strategy.AdjustmentCommandFactory;
import com.apps.deen_sa.finance.account.strategy.CreditSettlementStrategy;
import com.apps.deen_sa.finance.legacy.mutation.strategy.StateMutationStrategy;
import com.apps.deen_sa.finance.legacy.mutation.strategy.StateMutationStrategyResolver;
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
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LiabilityPaymentHandler implements SpeechHandler {

    private static final String CONFIRM_PAYMENT = "confirmLiabilityPayment";

    private final LiabilityPaymentClassifier llm;
    private final StateChangeRepository transactionRepository;
    private final StateContainerService stateContainerService;
    private final StateMutationService stateMutationService;
    private final StateMutationRepository stateMutationRepository;
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
        Long userId = ctx.getUserId();
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

        ctx.setActiveIntent(intentType());
        ctx.setWaitingForField(CONFIRM_PAYMENT);
        ctx.setPartialObject(dto);
        ctx.setActiveTransactionId(null);

        return SpeechResult.followup(
                "I identified:\n"
                        + "Amount: ₹" + dto.getAmount().stripTrailingZeros().toPlainString() + "\n"
                        + "From: " + sourceContainer.getName() + "\n"
                        + "To: " + targetContainer.getName() + "\n\n"
                        + "Confirm this payment?",
                List.of(CONFIRM_PAYMENT),
                dto,
                List.of(
                        new ResponseAction("answer:CONFIRM_LIABILITY_PAYMENT", "Confirm"),
                        new ResponseAction("answer:DISCARD_LIABILITY_PAYMENT", "Discard")));
    }

    private SpeechResult saveConfirmedPayment(LiabilityPaymentDto dto, ConversationContext ctx) {
        Long userId = ctx.getUserId();
        StateContainerEntity sourceContainer = resolveSourceContainer(dto, userId);
        StateContainerEntity targetContainer = resolveTargetLiability(dto, userId);
        if (sourceContainer == null || targetContainer == null) {
            ctx.reset();
            return SpeechResult.invalid(
                    "The payment accounts could not be resolved anymore. Nothing was saved; please try again.");
        }

        // Create transaction entity only after explicit authorization.
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

        return SpeechResult.builder()
                .status(com.apps.deen_sa.conversation.SpeechStatus.SAVED)
                .message("Paid ₹" + saved.getAmount().stripTrailingZeros().toPlainString()
                        + " from " + sourceContainer.getName() + " to " + targetContainer.getName() + ".")
                .savedEntity(saved)
                .needFollowup(false)
                .build();
    }

    @Override
    public SpeechResult handleFollowup(String answer, ConversationContext ctx) {
        if (!CONFIRM_PAYMENT.equals(ctx.getWaitingForField())
                || !(ctx.getPartialObject() instanceof LiabilityPaymentDto dto)) {
            return SpeechResult.invalid("No liability payment is waiting for confirmation.");
        }
        if ("DISCARD_LIABILITY_PAYMENT".equalsIgnoreCase(answer)) {
            ctx.reset();
            return SpeechResult.info("Discarded. Nothing was saved.");
        }
        if (!"CONFIRM_LIABILITY_PAYMENT".equalsIgnoreCase(answer)) {
            return SpeechResult.followup(
                    "Please confirm or discard the pending payment.",
                    List.of(CONFIRM_PAYMENT), dto,
                    List.of(
                            new ResponseAction("answer:CONFIRM_LIABILITY_PAYMENT", "Confirm"),
                            new ResponseAction("answer:DISCARD_LIABILITY_PAYMENT", "Discard")));
        }
        return saveConfirmedPayment(dto, ctx);
    }

    /**
     * Resolve source container (where money comes from).
     * Defaults to BANK_ACCOUNT if not specified.
     */
    StateContainerEntity resolveSourceContainer(LiabilityPaymentDto dto, Long userId) {
        return resolveSourceContainer(dto, stateContainerService.getActiveContainers(userId));
    }

    static StateContainerEntity resolveSourceContainer(
            LiabilityPaymentDto dto, List<StateContainerEntity> containers) {

        String sourceType = dto.getSourceAccount() != null ? dto.getSourceAccount() : "BANK_ACCOUNT";

        // Find matching container by type
        List<StateContainerEntity> matching = containers.stream()
                .filter(c -> c.getContainerType().equals(sourceType))
                .toList();

        if (matching.size() <= 1) {
            return matching.isEmpty() ? null : matching.getFirst();
        }

        // The extractor currently returns the source account type, not its name. When a user has
        // several bank accounts, use the account name explicitly present in the original message.
        // Never debit whichever account happens to be returned first by the repository.
        String rawText = dto.getRawText() == null ? "" : normalizeMention(dto.getRawText());
        List<StateContainerEntity> namedMatches = matching.stream()
                .filter(c -> c.getName() != null && rawText.contains(normalizeMention(c.getName())))
                .toList();

        return namedMatches.size() == 1 ? namedMatches.getFirst() : null;
    }

    private static String normalizeMention(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
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

        // With multiple liabilities, a stated name must resolve exactly enough to one card.
        // Never silently post a payment to the first credit card.
        if (dto.getTargetName() != null && !matching.isEmpty()) {
            String requested = normalizeAccountName(dto.getTargetName());
            List<StateContainerEntity> namedMatches = matching.stream()
                    .filter(c -> c.getName() != null &&
                            namesMatch(normalizeAccountName(c.getName()), requested))
                    .toList();

            if (namedMatches.size() == 1) {
                return namedMatches.get(0);
            }
            return null;
        }

        // An omitted name is safe only when exactly one liability of that type exists.
        return matching.size() == 1 ? matching.getFirst() : null;
    }

    private boolean namesMatch(String stored, String requested) {
        return !stored.isBlank() && !requested.isBlank()
                && (stored.equals(requested) || stored.contains(requested) || requested.contains(stored));
    }

    private String normalizeAccountName(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("credit card", "")
                .replace("card", "")
                .replace("bill", "")
                .replace("payment", "")
                .replaceAll("[^\\p{L}\\p{N}]", "")
                .trim();
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
            // Create adjustment record for credit card payment
            StateMutationEntity creditAdjustment = new StateMutationEntity();
            creditAdjustment.setTransactionId(tx.getId());
            creditAdjustment.setContainerId(targetContainer.getId());
            creditAdjustment.setAdjustmentType(MutationTypeEnum.CREDIT);
            creditAdjustment.setAmount(tx.getAmount());
            creditAdjustment.setReason(reason);
            creditAdjustment.setOccurredAt(tx.getTimestamp() != null ? tx.getTimestamp() : Instant.now());
            creditAdjustment.setCreatedAt(Instant.now());
            stateMutationRepository.save(creditAdjustment);
            
            // Apply payment to reduce outstanding
            ((CreditSettlementStrategy) strategy).applyPayment(targetContainer, tx.getAmount());
            // Save updated container
            targetContainer.setLastActivityAt(Instant.now());
            stateContainerService.UpdateValueContainer(targetContainer);
        } else {
            throw new IllegalStateException(
                    "Target container does not support liability payments"
            );
        }
    }
}
