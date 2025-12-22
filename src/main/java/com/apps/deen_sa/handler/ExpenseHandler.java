package com.apps.deen_sa.handler;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.entity.TransactionEntity;
import com.apps.deen_sa.entity.ValueContainerEntity;
import com.apps.deen_sa.evaluator.ExpenseCompletenessEvaluator;
import com.apps.deen_sa.llm.impl.ExpenseClassifier;
import com.apps.deen_sa.mapper.ExpenseDtoToEntityMapper;
import com.apps.deen_sa.orchestrator.ConversationContext;
import com.apps.deen_sa.orchestrator.SpeechHandler;
import com.apps.deen_sa.orchestrator.SpeechResult;
import com.apps.deen_sa.repo.TransactionRepository;
import com.apps.deen_sa.service.TagNormalizationService;
import com.apps.deen_sa.service.ValueContainerService;
import com.apps.deen_sa.utils.CompletenessLevelEnum;
import com.apps.deen_sa.utils.ExpenseMerger;
import com.apps.deen_sa.validator.ExpenseValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpenseHandler implements SpeechHandler {

    private final ExpenseClassifier llm;
    private final TransactionRepository repo;
    private final TagNormalizationService tagNormalizationService;
    private final ValueContainerService valueContainerService;
    private final ExpenseCompletenessEvaluator completenessEvaluator;

    @Override
    public String intentType() {
        return "EXPENSE";
    }

    /**
     * PRIMARY ENTRY POINT: first time user speaks an expense sentence
     */
    @Override
    public SpeechResult handleSpeech(String text, ConversationContext ctx) {

        ExpenseDto dto = llm.extractExpense(text);

        CompletenessLevelEnum level =
                completenessEvaluator.evaluate(dto);

        if (level == null) {
            return SpeechResult.invalid("I couldn’t understand this expense clearly.");
        }

        // Always find missing fields (used for UI / follow-up)
        List<String> missing = ExpenseValidator.findMissingFields(dto);

        // 🔹 CASE 1: MINIMAL completeness
        // We SAVE, but still ask follow-up to improve quality
        if (level == CompletenessLevelEnum.MINIMAL) {

            TransactionEntity saved = saveExpense(dto); // sourceContainerId will be NULL
            saved.setNeedsEnrichment(true);
            saved.setFinanciallyApplied(false);
            repo.save(saved);

            // If there are missing fields, guide the user
            if (!missing.isEmpty()) {
                String nextField = missing.getFirst();
                String followupQ =
                        llm.generateFollowupQuestionForExpense(nextField, dto);

                ctx.setActiveIntent("EXPENSE");
                ctx.setWaitingForField(nextField);
                ctx.setPartialObject(dto);
                ctx.setActiveTransactionId(saved.getId());

                return SpeechResult.followup(
                        followupQ,
                        List.of(nextField),
                        dto
                );
            }

            ctx.reset();
            return SpeechResult.saved(saved);
        }

        // 🔹 CASE 2: OPERATIONAL completeness
        // Save + map container, but no balance mutation
        if (level == CompletenessLevelEnum.OPERATIONAL) {

            TransactionEntity saved = saveExpense(dto);
            saved.setNeedsEnrichment(false);
            saved.setFinanciallyApplied(false);
            repo.save(saved);
            ctx.reset();

            return SpeechResult.saved(saved);
        }

        // 🔹 CASE 3: FINANCIAL completeness
        // Full save + balance mutation
        if (level == CompletenessLevelEnum.FINANCIAL) {

            TransactionEntity saved = saveExpense(dto);
            if (!saved.isFinanciallyApplied()) {
                applyFinancialImpact(saved);
                saved.setFinanciallyApplied(true);
            }

            saved.setNeedsEnrichment(false);
            repo.save(saved);
            ctx.reset();

            return SpeechResult.saved(saved);
        }

        // Should never reach here
        return SpeechResult.invalid("Unexpected expense state.");
    }

    /**
     * FOLLOW-UP HANDLER: user answers missing fields
     */
    @Override
    public SpeechResult handleFollowup(String userAnswer, ConversationContext ctx) {

        String missingField = ctx.getWaitingForField();
        ExpenseDto dto = (ExpenseDto) ctx.getPartialObject();
        Long transactionId = ctx.getActiveTransactionId();

        if (transactionId == null) {
            return SpeechResult.invalid("No active transaction to update.");
        }

        // ----------------------------
        // Step A – Extract refined field from LLM
        // ----------------------------
        ExpenseDto refined =
                llm.extractFieldFromFollowup(dto, missingField, userAnswer);

        // ----------------------------
        // Step B – Merge into existing DTO
        // ----------------------------
        ExpenseMerger.merge(dto, refined);
        dto.setRawText(dto.getRawText() + " " + userAnswer);

        // ----------------------------
        // Step C – Re-evaluate completeness
        // ----------------------------
        CompletenessLevelEnum newLevel =
                completenessEvaluator.evaluate(dto);

        if (newLevel == null) {
            return SpeechResult.invalid("Updated data is still invalid.");
        }

        // ----------------------------
        // Step D – Load existing transaction
        // ----------------------------
        TransactionEntity tx =
                repo.findById(transactionId)
                        .orElseThrow(() ->
                                new IllegalStateException("Transaction not found"));

        // ----------------------------
        // Step E – Merge DTO into entity
        // ----------------------------
        ExpenseDtoToEntityMapper.updateEntity(tx, dto);
        tx.setCompletenessLevel(newLevel);

        // ----------------------------
        // Step F – needs_enrichment flag
        // ----------------------------
        tx.setNeedsEnrichment(newLevel == CompletenessLevelEnum.MINIMAL);

        // ----------------------------
        // Step G – Resolve source container if possible
        // ----------------------------
        if (newLevel != CompletenessLevelEnum.MINIMAL
                && tx.getSourceContainerId() == null) {

            ValueContainerEntity source =
                    resolveSourceContainer(dto, Long.valueOf(tx.getUserId()));

            if (source != null) {
                tx.setSourceContainerId(source.getId());
            }
        }

        // ----------------------------
        // Step H – Apply financial impact exactly once
        // ----------------------------
        if (newLevel == CompletenessLevelEnum.FINANCIAL
                && !tx.isFinanciallyApplied()) {

            applyFinancialImpact(tx);
            tx.setFinanciallyApplied(true);
        }

        // ----------------------------
        // Step I – Persist updates
        // ----------------------------
        repo.save(tx);

        // ----------------------------
        // Step J – Check if more follow-ups are needed
        // ----------------------------
        List<String> stillMissing =
                ExpenseValidator.findMissingFields(dto);

        if (!stillMissing.isEmpty()) {

            String nextField = stillMissing.getFirst();
            String followupQ =
                    llm.generateFollowupQuestionForExpense(nextField, dto);

            ctx.setWaitingForField(nextField);
            ctx.setPartialObject(dto);
            // activeTransactionId remains untouched

            return SpeechResult.followup(
                    followupQ,
                    List.of(nextField),
                    dto
            );
        }

        // ----------------------------
        // Step K – Conversation complete
        // ----------------------------
        ctx.reset();
        return SpeechResult.saved(tx);
    }

    // -----------------------------------------------------
    // INTERNAL SAVE LOGIC
    // -----------------------------------------------------
    private TransactionEntity saveExpense(ExpenseDto dto) {
        Long userId = 1L; // TODO: resolve properly
        dto.setTags(tagNormalizationService.normalizeTags(dto.getTags()));

        TransactionEntity transaction =
                ExpenseDtoToEntityMapper.toEntity(dto, userId);

        // Resolve source container only if provided
        ValueContainerEntity source =
                resolveSourceContainer(dto, userId);

        if (source != null) {
            transaction.setSourceContainerId(source.getId());
        }

        return repo.save(transaction);
    }

    private ValueContainerEntity resolveSourceContainer(ExpenseDto dto, Long userId) {

        if (dto.getSourceAccount() == null) {
            return null;
        }

        List<ValueContainerEntity> containers =
                valueContainerService.getActiveContainers(userId);

        List<ValueContainerEntity> matching =
                containers.stream()
                        .filter(c ->
                                c.getContainerType()
                                        .equals(dto.getSourceAccount())
                        )
                        .toList();

        if (matching.isEmpty()) {
            throw new IllegalStateException(
                    "No active value container found for source account type: "
                            + dto.getSourceAccount()
            );
        }

        if (matching.size() > 1) {
            throw new IllegalStateException(
                    "Multiple value containers found for source account type: "
                            + dto.getSourceAccount()
                            + ". Ambiguous resolution."
            );
        }

        return matching.getFirst();
    }

    private void applyFinancialImpact(TransactionEntity tx) {

        if (tx.isFinanciallyApplied()) {
            return; // idempotency guard
        }

        if (tx.getSourceContainerId() == null) {
            throw new IllegalStateException(
                    "Cannot apply financial impact without source container"
            );
        }

        ValueContainerEntity container =
                valueContainerService.findValueContainerById(tx.getSourceContainerId());

        BigDecimal amount = tx.getAmount();

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("Invalid transaction amount");
        }

        switch (container.getContainerType()) {

            case "CASH", "BANK" -> {
                applyDebit(container, amount);
            }

            case "CREDIT" -> {
                applyCreditSpend(container, amount);
            }

            default -> throw new IllegalStateException(
                    "Unsupported container type: " + container.getContainerType()
            );
        }

        container.setLastActivityAt(Instant.now());
        valueContainerService.UpdateValueContainer(container);
    }

    private void applyDebit(ValueContainerEntity container,
                            BigDecimal amount) {

        BigDecimal available = container.getAvailableValue();

        if (available == null) {
            throw new IllegalStateException(
                    "Available value is null for container " + container.getId()
            );
        }

        BigDecimal newAvailable = available.subtract(amount);

        container.setAvailableValue(newAvailable);
        container.setCurrentValue(newAvailable); // keep them aligned for debit accounts
    }

    private void applyCreditSpend(ValueContainerEntity container,
                                  BigDecimal amount) {

        BigDecimal available = container.getAvailableValue();

        if (available == null) {
            throw new IllegalStateException(
                    "Available credit is null for container " + container.getId()
            );
        }

        // Reduce available credit
        container.setAvailableValue(available.subtract(amount));

        // Increase outstanding in details
        Map<String, Object> details =
                container.getDetails() != null
                        ? new HashMap<>(container.getDetails())
                        : new HashMap<>();

        BigDecimal outstanding =
                details.get("outstanding") instanceof Number n
                        ? new BigDecimal(n.toString())
                        : BigDecimal.ZERO;

        details.put("outstanding", outstanding.add(amount));
        container.setDetails(details);
    }
}
