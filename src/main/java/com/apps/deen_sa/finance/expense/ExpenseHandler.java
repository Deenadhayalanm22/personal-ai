package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.llm.impl.ExpenseClassifier;
import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.account.strategy.AdjustmentCommandFactory;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationService;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
import com.apps.deen_sa.finance.legacy.state.CompletenessLevelEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;
import com.apps.deen_sa.conversation.ResponseAction;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

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
        return handleExpense(dto, ctx);
    }

    /** Executes already-interpreted facts. No model call and no semantic parsing happens here. */
    public SpeechResult handleInterpreted(EventPatch patch, String rawText, ConversationContext ctx) {
        ExpenseDto extracted = objectMapper.convertValue(patch.fields().asMap(), ExpenseDto.class);
        extracted.setRawText(rawText);
        extracted.setValid(extracted.getAmount() != null);
        ExpenseDto dto = inputNormalizer.normalize(extracted, rawText, ctx);
        return handleExpense(dto, ctx);
    }

    private SpeechResult handleExpense(ExpenseDto dto, ConversationContext ctx) {

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
            ctx.setActiveIntent("EXPENSE");
            ctx.setWaitingForField("amount");
            ctx.setPartialObject(dto);
            ctx.setActiveTransactionId(null);
            return SpeechResult.followup("How much did you spend?", List.of("amount"), dto, List.of());
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
        if ("creditLimit".equals(missingField)) {
            return completeCreditLimit(userAnswer, ctx, transactionId);
        }
        if ("creditCardDueDay".equals(missingField)) {
            return completeCreditCardDueDay(userAnswer, ctx, transactionId);
        }

        // ----------------------------
        // Step A – Extract refined field from LLM
        // ----------------------------
        ExpenseDto refined =
                llm.extractFieldFromFollowup(dto, missingField, userAnswer);

        return completeInterpretedFollowup(refined, userAnswer, ctx);
    }

    /** Applies an interpreter-produced patch to the pending expense without another model call. */
    public SpeechResult handleInterpretedFollowup(EventPatch patch, String userAnswer, ConversationContext ctx) {
        if ("creditLimit".equals(ctx.getWaitingForField())) {
            return completeCreditLimit(userAnswer, ctx, ctx.getActiveTransactionId());
        }
        if ("creditCardDueDay".equals(ctx.getWaitingForField())) {
            return completeCreditCardDueDay(userAnswer, ctx, ctx.getActiveTransactionId());
        }
        ExpenseDto refined = objectMapper.convertValue(patch.fields().asMap(), ExpenseDto.class);
        return completeInterpretedFollowup(refined, userAnswer, ctx);
    }

    private SpeechResult completeInterpretedFollowup(ExpenseDto refined, String userAnswer, ConversationContext ctx) {
        String missingField = ctx.getWaitingForField();
        ExpenseDto dto = (ExpenseDto) ctx.getPartialObject();
        Long transactionId = ctx.getActiveTransactionId();

        if (transactionId == null && "amount".equals(missingField)) {
            ExpenseMerger.merge(dto, refined);
            dto.setRawText(dto.getRawText() + " " + userAnswer);
            return handleExpense(dto, ctx);
        }
        if (transactionId == null) return SpeechResult.invalid("No active transaction to update.");
        if ("sourceBalance".equals(missingField)) return completeSourceBalance(userAnswer, ctx, transactionId);

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
            StateContainerEntity source = stateContainerService.findValueContainerById(tx.getSourceContainerId());
            if ("CREDIT_CARD".equals(source.getContainerType()) && source.getCapacityLimit() == null) {
                ctx.setWaitingForField("creditLimit");
                ctx.setPartialObject(dto);
                return SpeechResult.followup(
                        "I created " + source.getName() + ". What is its credit limit?",
                        List.of("creditLimit"), dto,
                        List.of(new ResponseAction("control:skip", "Not sure / later"))
                );
            }
            ctx.setWaitingForField("sourceBalance");
            ctx.setPartialObject(dto);
            return SpeechResult.followup(
                    "I created " + source.getName() + ". " + balanceQuestion(source),
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
        if ("category".equals(field)) {
            return List.of(
                    new ResponseAction("answer:Groceries", "Groceries"),
                    new ResponseAction("answer:Food and dining", "Food / Dining"),
                    new ResponseAction("answer:Transportation", "Travel")
            );
        }
        if ("sourceAccount".equals(field)) {
            return List.of(
                    new ResponseAction("answer:CASH", "Cash"),
                    new ResponseAction("answer:BANK_ACCOUNT", "Bank / UPI"),
                    new ResponseAction("answer:CREDIT_CARD", "Credit Card"),
                    new ResponseAction("control:skip", "Skip")
            );
        }
        return List.of(new ResponseAction("control:skip", "Skip for now"));
    }

    private String questionFor(String field, ExpenseDto dto) {
        return switch (field) {
            case "category" -> "What was the ₹" + dto.getAmount().stripTrailingZeros().toPlainString()
                    + " expense for? Reply with something like groceries, fuel, rent, or choose below. Type Skip to leave it uncategorized.";
            case "sourceAccount" -> "How did you pay?";
            case "amount" -> "How much did you spend?";
            case "spentAt" -> "When did you spend it?";
            default -> "Please provide " + field + ".";
        };
    }

    private SpeechResult completeSourceBalance(String answer, ConversationContext ctx, Long transactionId) {
        BigDecimal balance;
        try {
            balance = HumanAmountParser.parse(answer).orElseThrow(NumberFormatException::new);
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
        String valueLabel = "CREDIT_CARD".equals(source.getContainerType()) ? " outstanding is now ₹" : " balance is now ₹";
        return SpeechResult.builder()
                .status(com.apps.deen_sa.conversation.SpeechStatus.SAVED)
                .message("All set. Added ₹" + tx.getAmount().stripTrailingZeros().toPlainString()
                        + " for " + tx.getCategory() + ". " + source.getName()
                        + valueLabel + updated.getCurrentValue().stripTrailingZeros().toPlainString() + ".")
                .savedEntity(tx)
                .needFollowup(false)
                .build();
    }

    private SpeechResult completeCreditLimit(String answer, ConversationContext ctx, Long transactionId) {
        BigDecimal limit;
        try {
            limit = HumanAmountParser.parse(answer).orElseThrow(NumberFormatException::new);
            if (limit.signum() <= 0) throw new NumberFormatException("non-positive limit");
        } catch (RuntimeException invalidNumber) {
            return SpeechResult.followup("Please enter the credit limit as a number, for example 50k.",
                    List.of("creditLimit"), ctx.getPartialObject(),
                    List.of(new ResponseAction("control:skip", "Not sure / later")));
        }
        StateChangeEntity tx = repo.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("Transaction not found"));
        StateContainerEntity source = stateContainerService.findValueContainerById(tx.getSourceContainerId());
        source.setCapacityLimit(limit);
        stateContainerService.UpdateValueContainer(source);
        ctx.setWaitingForField("creditCardDueDay");
        return SpeechResult.followup("What day of the month is this card's payment due? For example, reply 21 for the 21st.",
                List.of("creditCardDueDay"), ctx.getPartialObject(),
                List.of(new ResponseAction("answer:UNKNOWN_DUE_DAY", "Not sure")));
    }

    private SpeechResult completeCreditCardDueDay(String answer, ConversationContext ctx, Long transactionId) {
        StateChangeEntity tx = repo.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("Transaction not found"));
        StateContainerEntity source = stateContainerService.findValueContainerById(tx.getSourceContainerId());
        if (!"UNKNOWN_DUE_DAY".equals(answer)) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|\\D)([0-9]{1,2})(?:st|nd|rd|th)?(?:\\D|$)",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(answer.trim());
            if (!matcher.find()) {
                return invalidDueDay(ctx);
            }
            int dueDay = Integer.parseInt(matcher.group(1));
            if (dueDay < 1 || dueDay > 31) return invalidDueDay(ctx);
            java.util.Map<String, Object> details = source.getDetails() == null
                    ? new java.util.HashMap<>() : new java.util.HashMap<>(source.getDetails());
            details.put("dueDay", dueDay);
            source.setDetails(details);
            stateContainerService.UpdateValueContainer(source);
        }
        ctx.setWaitingForField("sourceBalance");
        return SpeechResult.followup("What is the card's current outstanding amount? Reply 0 if nothing is due.",
                List.of("sourceBalance"), ctx.getPartialObject(),
                List.of(new ResponseAction("answer:0", "Nothing due"),
                        new ResponseAction("control:skip", "Not sure / later")));
    }

    private SpeechResult invalidDueDay(ConversationContext ctx) {
        return SpeechResult.followup("Please enter a due day from 1 to 31, or choose Not sure.",
                List.of("creditCardDueDay"), ctx.getPartialObject(),
                List.of(new ResponseAction("answer:UNKNOWN_DUE_DAY", "Not sure")));
    }

    private String balanceQuestion(StateContainerEntity source) {
        return "CREDIT_CARD".equals(source.getContainerType())
                ? "What is the card's current outstanding amount? Reply 0 if nothing is due."
                : "What is its current balance?";
    }

    private SpeechResult expenseConfirmation(StateChangeEntity expense) {
        String category = expense.getCategory();
        boolean hasCategory = category != null && !category.isBlank()
                && !java.util.Set.of("null", "none", "n/a", "unknown").contains(category.trim().toLowerCase());
        String message = "Added ₹" + expense.getAmount().stripTrailingZeros().toPlainString()
                + (hasCategory ? " for " + category + "." : " expense.");
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
