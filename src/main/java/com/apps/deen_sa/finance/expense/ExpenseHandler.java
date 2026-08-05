package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.core.state.StateChangeEntity;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.llm.impl.ExpenseClassifier;
import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.core.state.StateChangeRepository;
import com.apps.deen_sa.finance.account.strategy.AdjustmentCommandFactory;
import com.apps.deen_sa.core.mutation.StateMutationService;
import com.apps.deen_sa.core.state.StateContainerService;
import com.apps.deen_sa.core.state.CompletenessLevelEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;
import com.apps.deen_sa.conversation.ResponseAction;

@Service
@RequiredArgsConstructor
@Log4j2
public class ExpenseHandler implements SpeechHandler {

    private final ExpenseClassifier llm;
    private final StateChangeRepository repo;
    private final StateContainerService stateContainerService;
    private final ExpenseCompletenessEvaluator completenessEvaluator;
    private final AdjustmentCommandFactory adjustmentCommandFactory;
    private final StateMutationService stateMutationService;
    private final ExpenseInputNormalizer inputNormalizer;

    @Override
    public String intentType() {
        return "EXPENSE";
    }

    /**
     * PRIMARY ENTRY POINT: first time user speaks an expense sentence
     */
    @Override
    public SpeechResult handleSpeech(String text, ConversationContext ctx) {

        ExpenseDto dto = inputNormalizer.normalize(llm.extractExpense(text), text, ctx);

        log.info("Expense extraction completeness - amountPresent={}, categoryPresent={}, datePresent={}, "
                        + "sourcePresent={}, extractorValid={}, extractorReason={}",
                dto.getAmount() != null,
                dto.getCategory() != null && !dto.getCategory().isBlank(),
                dto.getTransactionDate() != null,
                dto.getSourceAccount() != null,
                dto.isValid(),
                dto.getReason());

        CompletenessLevelEnum level =
                completenessEvaluator.evaluate(dto);

        if (level == null) {
            log.warn("Rejecting expense because no explicit amount could be extracted from text");
            return SpeechResult.invalid("How much did you spend?");
        }

        // Always find missing fields (used for UI / follow-up)
        List<String> missing = ExpenseValidator.findMissingFields(dto);

        // 🔹 CASE 1: MINIMAL completeness
        // We SAVE, but still ask follow-up to improve quality
        if (level == CompletenessLevelEnum.MINIMAL) {

            StateChangeEntity saved = saveExpense(dto, ctx.getUserId()); // sourceContainerId will be NULL
            saved.setNeedsEnrichment(true);
            saved.setFinanciallyApplied(false);
            repo.save(saved);

            // If there are missing fields, guide the user
            if (!missing.isEmpty()) {
                String nextField = missing.getFirst();
                String followupQ = questionFor(nextField, dto);

                ctx.setActiveIntent("EXPENSE");
                ctx.setWaitingForField(nextField);
                ctx.setPartialObject(dto);
                ctx.setActiveTransactionId(saved.getId());

                return SpeechResult.followup(
                        followupQ,
                        List.of(nextField),
                        dto,
                        actionsFor(nextField)
                );
            }

            ctx.reset();
            return SpeechResult.saved(saved);
        }

        // 🔹 CASE 2: OPERATIONAL completeness
        // Save + map container, but no balance mutation
        if (level == CompletenessLevelEnum.OPERATIONAL) {

            StateChangeEntity saved = saveExpense(dto, ctx.getUserId());
            // based on the container, enrichment being handled.
            saved.setNeedsEnrichment(saved.getSourceContainerId() == null || !canApplyFinancialImpact(saved));

            // ✅ APPLY FINANCIAL IMPACT IF POSSIBLE
            if (canApplyFinancialImpact(saved)
                    && !saved.isFinanciallyApplied()) {

                applyFinancialImpact(saved);
                saved.setFinanciallyApplied(true);
            }

            repo.save(saved);

            ctx.reset();

            return expenseConfirmation(saved);
        }

        // 🔹 CASE 3: FINANCIAL completeness
        // Full save + balance mutation
        if (level == CompletenessLevelEnum.FINANCIAL) {

            StateChangeEntity saved = saveExpense(dto, ctx.getUserId());
            if (canApplyFinancialImpact(saved) && !saved.isFinanciallyApplied()) {
                applyFinancialImpact(saved);
                saved.setFinanciallyApplied(true);
            }

            saved.setNeedsEnrichment(!saved.isFinanciallyApplied());
            repo.save(saved);
            ctx.reset();

            return expenseConfirmation(saved);
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

        if ("sourceBalance".equals(missingField)) {
            return completeSourceBalance(userAnswer, ctx, transactionId);
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
        if (canApplyFinancialImpact(tx)
                && !tx.isFinanciallyApplied()) {

            applyFinancialImpact(tx);
            tx.setFinanciallyApplied(true);
        }
        tx.setNeedsEnrichment(!tx.isFinanciallyApplied());

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
            String followupQ = questionFor(nextField, dto);

            ctx.setWaitingForField(nextField);
            ctx.setPartialObject(dto);
            // activeTransactionId remains untouched

            return SpeechResult.followup(
                    followupQ,
                    List.of(nextField),
                    dto,
                    actionsFor(nextField)
            );
        }


        if (tx.getSourceContainerId() != null && !canApplyFinancialImpact(tx)) {
            ctx.setWaitingForField("sourceBalance");
            ctx.setPartialObject(dto);
            return SpeechResult.followup(
                    "I created " + stateContainerService.findValueContainerById(tx.getSourceContainerId()).getName()
                            + ". What is its current balance?",
                    List.of("sourceBalance"),
                    dto,
                    List.of(new ResponseAction("control:skip", "Not sure / later"))
            );
        }

        // ----------------------------
        // Step K – Conversation complete
        // ----------------------------
        ctx.reset();
        return expenseConfirmation(tx);
    }

    // -----------------------------------------------------
    // INTERNAL SAVE LOGIC
    // -----------------------------------------------------
    private StateChangeEntity saveExpense(ExpenseDto dto, Long userId) {

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

        String requested = normalizeSourceType(dto.getSourceAccount());
        List<StateContainerEntity> matching =
                containers.stream()
                        .filter(c -> c.getContainerType().equals(requested)
                                || c.getName().equalsIgnoreCase(dto.getSourceAccount()))
                        .toList();

        if (matching.size() == 1) return matching.getFirst();
        if (matching.isEmpty() && isSupportedSource(requested)) {
            return stateContainerService.createProvisional(userId, requested);
        }
        return null;
    }

    private String normalizeSourceType(String source) {
        String normalized = source.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (normalized.equals("BANK") || normalized.equals("UPI") || normalized.equals("BANK/UPI")) {
            return "BANK_ACCOUNT";
        }
        if (normalized.equals("CARD") || normalized.equals("CREDIT")) return "CREDIT_CARD";
        return normalized;
    }

    private boolean isSupportedSource(String type) {
        return List.of("CASH", "BANK_ACCOUNT", "CREDIT_CARD", "WALLET").contains(type);
    }

    private boolean canApplyFinancialImpact(StateChangeEntity tx) {
        if (tx.getSourceContainerId() == null) return false;
        return stateContainerService.findValueContainerById(tx.getSourceContainerId()).getCurrentValue() != null;
    }

    private List<ResponseAction> actionsFor(String field) {
        if ("sourceAccount".equals(field)) {
            return List.of(
                    new ResponseAction("answer:CASH", "Cash"),
                    new ResponseAction("answer:BANK_ACCOUNT", "Bank / UPI"),
                    new ResponseAction("control:skip", "Skip")
            );
        }
        return List.of(new ResponseAction("control:skip", "Skip for now"));
    }

    private String questionFor(String field, ExpenseDto dto) {
        return switch (field) {
            case "category" -> "What was the ₹" + dto.getAmount().stripTrailingZeros().toPlainString() + " expense for?";
            case "sourceAccount" -> "How did you pay?";
            case "amount" -> "How much did you spend?";
            case "spentAt" -> "When did you spend it?";
            default -> "Please provide " + field + ".";
        };
    }

    private SpeechResult completeSourceBalance(String answer, ConversationContext ctx, Long transactionId) {
        BigDecimal balance;
        try {
            String numeric = answer.replace(",", "").replaceAll("[^0-9.-]", "");
            balance = new BigDecimal(numeric);
            if (balance.signum() < 0) throw new NumberFormatException("negative balance");
        } catch (RuntimeException invalidNumber) {
            return SpeechResult.followup(
                    "Please enter the current balance as a number, or choose Not sure / later.",
                    List.of("sourceBalance"),
                    ctx.getPartialObject(),
                    List.of(new ResponseAction("control:skip", "Not sure / later"))
            );
        }

        StateChangeEntity tx = repo.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("Transaction not found"));
        StateContainerEntity source = stateContainerService.findValueContainerById(tx.getSourceContainerId());
        source.setCurrentValue(balance);
        source.setAvailableValue(balance);
        stateContainerService.UpdateValueContainer(source);

        if (!tx.isFinanciallyApplied()) {
            applyFinancialImpact(tx);
            tx.setFinanciallyApplied(true);
        }
        tx.setCompletenessLevel(CompletenessLevelEnum.FINANCIAL);
        tx.setNeedsEnrichment(false);
        repo.save(tx);
        ctx.reset();

        StateContainerEntity updated = stateContainerService.findValueContainerById(source.getId());
        return SpeechResult.builder()
                .status(com.apps.deen_sa.conversation.SpeechStatus.SAVED)
                .message("All set. Added ₹" + tx.getAmount().stripTrailingZeros().toPlainString()
                        + " for " + tx.getCategory() + ". " + source.getName()
                        + " balance is now ₹" + updated.getCurrentValue().stripTrailingZeros().toPlainString() + ".")
                .savedEntity(tx)
                .needFollowup(false)
                .build();
    }

    private SpeechResult expenseConfirmation(StateChangeEntity expense) {
        String message = "Added ₹" + expense.getAmount().stripTrailingZeros().toPlainString()
                + (expense.getCategory() == null ? " expense." : " for " + expense.getCategory() + ".");
        if (!expense.isFinanciallyApplied()) {
            message += " It is saved for spending insights; exact account balance is not updated yet.";
        }
        return SpeechResult.builder()
                .status(com.apps.deen_sa.conversation.SpeechStatus.SAVED)
                .message(message)
                .savedEntity(expense)
                .needFollowup(false)
                .build();
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
