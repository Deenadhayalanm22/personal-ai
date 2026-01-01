package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.core.state.StateChangeEntity;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.finance.expense.ExpenseCompletenessEvaluator;
import com.apps.deen_sa.llm.impl.ExpenseClassifier;
import com.apps.deen_sa.finance.expense.ExpenseDtoToEntityMapper;
import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.core.state.StateChangeRepository;
import com.apps.deen_sa.finance.account.strategy.AdjustmentCommandFactory;
import com.apps.deen_sa.finance.account.strategy.StateMutationStrategyResolver;
import com.apps.deen_sa.finance.expense.TagNormalizationService;
import com.apps.deen_sa.finance.account.StateMutationService;
import com.apps.deen_sa.finance.account.StateContainerService;
import com.apps.deen_sa.finance.account.strategy.StateMutationStrategy;
import com.apps.deen_sa.core.state.CompletenessLevelEnum;
import com.apps.deen_sa.finance.expense.ExpenseMerger;
import com.apps.deen_sa.finance.expense.ExpenseValidator;
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
    private final StateChangeRepository repo;
    private final TagNormalizationService tagNormalizationService;
    private final StateContainerService stateContainerService;
    private final ExpenseCompletenessEvaluator completenessEvaluator;
    private final AdjustmentCommandFactory adjustmentCommandFactory;
    private final StateMutationService stateMutationService;

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

            StateChangeEntity saved = saveExpense(dto); // sourceContainerId will be NULL
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

            StateChangeEntity saved = saveExpense(dto);
            // based on the container, enrichment being handled.
            saved.setNeedsEnrichment(saved.getSourceContainerId() == null);

            // ✅ APPLY FINANCIAL IMPACT IF POSSIBLE
            if (saved.getSourceContainerId() != null
                    && !saved.isFinanciallyApplied()) {

                applyFinancialImpact(saved);
                saved.setFinanciallyApplied(true);
            }

            repo.save(saved);
            ctx.reset();

            return SpeechResult.saved(saved);
        }

        // 🔹 CASE 3: FINANCIAL completeness
        // Full save + balance mutation
        if (level == CompletenessLevelEnum.FINANCIAL) {

            StateChangeEntity saved = saveExpense(dto);
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
        StateChangeEntity tx =
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

            StateContainerEntity source =
                    resolveSourceContainer(dto, Long.valueOf(tx.getUserId()));

            if (source != null) {
                tx.setSourceContainerId(source.getId());
            } else {
                // since value is not present we need further enrichment later on
                tx.setNeedsEnrichment(true);
            }
        }

        // ----------------------------
        // Step H – Apply financial impact exactly once
        // ----------------------------
        if (tx.getSourceContainerId() != null
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
    private StateChangeEntity saveExpense(ExpenseDto dto) {
        Long userId = 1L; // TODO: resolve properly
        dto.setTags(tagNormalizationService.normalizeTags(dto.getTags()));

        StateChangeEntity transaction =
                ExpenseDtoToEntityMapper.toEntity(dto, userId);

        // Resolve source container only if provided
        StateContainerEntity source =
                resolveSourceContainer(dto, userId);

        if (source != null) {
            transaction.setSourceContainerId(source.getId());
        }

        return repo.save(transaction);
    }

    private StateContainerEntity resolveSourceContainer(ExpenseDto dto, Long userId) {

        if (dto.getSourceAccount() == null) return null;

        List<StateContainerEntity> containers =
                stateContainerService.getActiveContainers(userId);

        List<StateContainerEntity> matching =
                containers.stream()
                        .filter(c -> c.getContainerType().equals(dto.getSourceAccount()))
                        .toList();

        return matching.size() == 1 ? matching.getFirst() : null;
    }

    // =====================================================
    // FINANCIAL APPLICATION (SINGLE SOURCE)
    // =====================================================
    private void applyFinancialImpact(StateChangeEntity tx) {

        if (tx.isFinanciallyApplied()) return;

        if (tx.getSourceContainerId() == null) {
            throw new IllegalStateException(
                    "Cannot apply financial impact without source container"
            );
        }

        StateContainerEntity container =
                stateContainerService.findValueContainerById(
                        tx.getSourceContainerId()
                );

        StateMutationCommand command =
                adjustmentCommandFactory.forExpense(tx);

        stateMutationService.apply(container, command);
    }
}
