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
import java.util.Set;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.apps.deen_sa.conversation.ResponseAction;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Log4j2
public class ExpenseHandler implements SpeechHandler {

    private static final String CONFIRM_EXPENSE = "confirmExpense";
    private static final String SETUP_SOURCE_ACCOUNT = "setupSourceAccount";
    private static final Pattern NAMED_CREDIT_CARD = Pattern.compile(
            "(?i)\\b(?:via|using|from|on|with)\\s+(?:my\\s+|the\\s+)?"
                    + "([\\p{L}\\p{N}]+(?:\\s+[\\p{L}\\p{N}]+){0,2}\\s+credit\\s+card)\\b");
    private static final Pattern NAMED_BANK_ACCOUNT = Pattern.compile(
            "(?i)\\b(?:via|using|from|on|with)\\s+(?:my\\s+|the\\s+)?"
                    + "([\\p{L}\\p{N}]+(?:\\s+[\\p{L}\\p{N}]+){0,2}\\s+bank\\s+account)\\b");

    private final ExpenseClassifier llm;
    private final StateChangeRepository repo;
    private final StateContainerService stateContainerService;
    private final ExpenseCompletenessEvaluator completenessEvaluator;
    private final AdjustmentCommandFactory adjustmentCommandFactory;
    private final StateMutationService stateMutationService;
    private final ExpenseInputNormalizer inputNormalizer;
    private final ObjectMapper objectMapper;
    private final com.apps.deen_sa.finance.budget.BudgetInsightService budgetInsights;

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

        // Minimal expenses remain only in conversation state until confirmation.
        if (level == CompletenessLevelEnum.MINIMAL) {
            if (!missing.isEmpty()) {
                String nextField = missing.getFirst();
                String followupQ = questionFor(nextField, dto);

                ctx.setActiveIntent("EXPENSE");
                ctx.setWaitingForField(nextField);
                ctx.setPartialObject(dto);
                ctx.setActiveTransactionId(null);

                return SpeechResult.followup(
                        followupQ,
                        List.of(nextField),
                        dto,
                        actionsFor(nextField)
                );
            }

            return confirmationPreview(dto, ctx);
        }

        // Operational data is presented for authorization before any write.
        if (level == CompletenessLevelEnum.OPERATIONAL) {
            return confirmationPreview(dto, ctx);
        }

        // Financially complete data still requires explicit authorization.
        if (level == CompletenessLevelEnum.FINANCIAL) {
            return confirmationPreview(dto, ctx);
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

        if (CONFIRM_EXPENSE.equals(missingField)) {
            return handleExpenseConfirmation(userAnswer, ctx);
        }
        if (SETUP_SOURCE_ACCOUNT.equals(missingField)) {
            return handleOptionalSourceSetup(userAnswer, ctx);
        }
        if (transactionId == null && "sourceAccount".equals(missingField)
                && "NO_SOURCE".equalsIgnoreCase(userAnswer)) {
            return confirmationPreview(dto, ctx);
        }

        if (transactionId != null && "sourceBalance".equals(missingField)) {
            return completeSourceBalance(userAnswer, ctx, transactionId);
        }
        if (transactionId != null && "creditLimit".equals(missingField)) {
            return completeCreditLimit(userAnswer, ctx, transactionId);
        }
        if (transactionId != null && "creditCardDueDay".equals(missingField)) {
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
        if (CONFIRM_EXPENSE.equals(ctx.getWaitingForField())
                || SETUP_SOURCE_ACCOUNT.equals(ctx.getWaitingForField())) {
            return handleFollowup(userAnswer, ctx);
        }
        if ("sourceAccount".equals(ctx.getWaitingForField())
                && "NO_SOURCE".equalsIgnoreCase(userAnswer)) {
            return confirmationPreview((ExpenseDto) ctx.getPartialObject(), ctx);
        }
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

        if (transactionId == null) {
            ExpenseMerger.merge(dto, refined);
            dto.setRawText(dto.getRawText() + " " + userAnswer);
            inputNormalizer.normalize(dto, dto.getRawText(), ctx);
            return handleExpense(dto, ctx);
        }
        if ("sourceBalance".equals(missingField)) return completeSourceBalance(userAnswer, ctx, transactionId);

        // ----------------------------
        // Step B – Merge into existing DTO
        // ----------------------------
        ExpenseMerger.merge(dto, refined);
        dto.setRawText(dto.getRawText() + " " + userAnswer);
        inputNormalizer.normalize(dto, dto.getRawText(), ctx);

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


        SpeechResult accountSetup = accountInitializationFollowup(tx, dto, ctx);
        if (accountSetup != null) return accountSetup;

        // ----------------------------
        // Step K – Conversation complete
        // ----------------------------
        ctx.reset();
        return expenseConfirmation(tx, ctx.getTimezone());
    }

    private SpeechResult accountInitializationFollowup(StateChangeEntity tx, ExpenseDto dto,
                                                       ConversationContext ctx) {
        if (tx.getSourceContainerId() == null || canApplyFinancialImpact(tx)) return null;
        StateContainerEntity source = stateContainerService.findValueContainerById(tx.getSourceContainerId());
        ctx.setActiveIntent("EXPENSE");
        ctx.setActiveTransactionId(tx.getId());
        ctx.setPartialObject(dto);
        if ("CREDIT_CARD".equals(source.getContainerType()) && source.getCapacityLimit() == null) {
            ctx.setWaitingForField("creditLimit");
            return SpeechResult.followup(
                    "I created " + source.getName() + ". What is its credit limit?",
                    List.of("creditLimit"), dto,
                    List.of(new ResponseAction("control:skip", "Not sure / later"))
            );
        }
        ctx.setWaitingForField("sourceBalance");
        return SpeechResult.followup(
                "I created " + source.getName() + ". " + balanceQuestion(source),
                List.of("sourceBalance"), dto,
                List.of(new ResponseAction("control:skip", "Not sure / later"))
        );
    }

    // -----------------------------------------------------
    // INTERNAL SAVE LOGIC
    // -----------------------------------------------------
    private StateChangeEntity saveExpense(ExpenseDto dto, Long userId) {

        // Persistence boundary invariant: never store an LLM label before it has
        // been reconciled with the configured category/subcategory hierarchy.
        inputNormalizer.normalize(dto, dto.getRawText(), new ConversationContext());

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
        String requestedName = normalizeAccountName(dto.getSourceAccount());
        List<StateContainerEntity> namedMatches =
                containers.stream()
                        .filter(c -> c.getName().equalsIgnoreCase(dto.getSourceAccount())
                                || normalizeAccountName(c.getName()).equals(requestedName)
                                || !requestedName.isBlank() && (
                                        normalizeAccountName(c.getName()).contains(requestedName)
                                                || requestedName.contains(normalizeAccountName(c.getName()))))
                        .toList();
        if (namedMatches.size() == 1) return namedMatches.getFirst();

        String compactSource = normalizeAccountName(dto.getSourceAccount()).toUpperCase(Locale.ROOT);
        boolean genericSource = Set.of("UPI", "BANK", "BANKUPI", "BANKACCOUNT", "CARD", "CREDIT",
                "CREDITCARD", "CASH", "WALLET").contains(compactSource);
        if (!genericSource) return null;
        List<StateContainerEntity> typeMatches = containers.stream()
                .filter(c -> c.getContainerType().equals(requested))
                .toList();
        if (typeMatches.size() == 1) return typeMatches.getFirst();
        if (typeMatches.size() > 1) {
            // Generic channels such as UPI do not identify a bank by name. Reuse
            // the user's last confirmed account of that type instead of offering
            // to create a duplicate account on every expense.
            Long recentId = repo.findMostRecentlyUsedActiveSourceId(userId.toString(), requested).orElse(null);
            return typeMatches.stream()
                    .filter(active -> java.util.Objects.equals(active.getId(), recentId))
                    .findFirst().orElse(null);
        }
        return null;
    }

    private String normalizeAccountName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceFirst("^(?:my|the)\\s+", "")
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static String normalizeSourceType(String source) {
        String normalized = source.trim().replaceFirst("(?i)^(?:my|the)\\s+", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        String compact = normalized.replace("_", "");
        if (Set.of("BANK", "UPI", "BANKUPI", "BANKACCOUNT").contains(compact)
                || compact.endsWith("BANKACCOUNT")) {
            return "BANK_ACCOUNT";
        }
        if (Set.of("CARD", "CREDIT", "CREDITCARD").contains(compact)
                || compact.endsWith("CREDITCARD")) return "CREDIT_CARD";
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
                    new ResponseAction("answer:NO_SOURCE", "Not now")
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

    private SpeechResult confirmationPreview(ExpenseDto dto, ConversationContext ctx) {
        dto.setSourceAccount(specificSourceAccount(dto));
        StateContainerEntity linked = resolveSourceContainer(dto, ctx.getUserId());
        String detected = displayValue(dto.getSourceAccount());
        String message = "I identified:\n"
                + "Amount: ₹" + dto.getAmount().stripTrailingZeros().toPlainString() + "\n"
                + "Category: " + displayValue(dto.getCategory()) + "\n"
                + "Subcategory: " + displayValue(dto.getSubcategory()) + "\n"
                + "Source: " + (linked == null ? "null" : linked.getName());
        if (linked == null && !"null".equals(detected)) {
            message += "\nDetected account: " + detected + " (not configured)";
        }
        message += "\n\nConfirm this expense?";

        ctx.setActiveIntent("EXPENSE");
        ctx.setWaitingForField(CONFIRM_EXPENSE);
        ctx.setPartialObject(dto);
        ctx.setActiveTransactionId(null);
        return SpeechResult.followup(message, List.of(CONFIRM_EXPENSE), dto, List.of(
                new ResponseAction("answer:CONFIRM_EXPENSE", "Confirm"),
                new ResponseAction("answer:DISCARD_EXPENSE", "Discard")
        ));
    }

    private SpeechResult handleExpenseConfirmation(String answer, ConversationContext ctx) {
        if ("DISCARD_EXPENSE".equalsIgnoreCase(answer)) {
            ctx.reset();
            return SpeechResult.info("Discarded. Nothing was saved.");
        }
        if (!"CONFIRM_EXPENSE".equalsIgnoreCase(answer)) {
            return confirmationPreview((ExpenseDto) ctx.getPartialObject(), ctx);
        }

        ExpenseDto dto = (ExpenseDto) ctx.getPartialObject();
        StateChangeEntity saved = saveExpense(dto, ctx.getUserId());
        if (canApplyFinancialImpact(saved)) {
            applyFinancialImpact(saved);
            saved.setFinanciallyApplied(true);
        }
        saved.setNeedsEnrichment(!saved.isFinanciallyApplied());

        // A linked but incomplete account is still optional to finish configuring.
        SpeechResult existingAccountSetup = accountInitializationFollowup(saved, dto, ctx);
        if (existingAccountSetup != null) return existingAccountSetup;

        if (saved.getSourceContainerId() == null && dto.getSourceAccount() != null
                && isSupportedSource(normalizeSourceType(dto.getSourceAccount()))) {
            ctx.setActiveIntent("EXPENSE");
            ctx.setWaitingForField(SETUP_SOURCE_ACCOUNT);
            ctx.setPartialObject(dto);
            ctx.setActiveTransactionId(saved.getId());
            return SpeechResult.builder()
                    .status(com.apps.deen_sa.conversation.SpeechStatus.FOLLOWUP)
                    .message("Added ₹" + saved.getAmount().stripTrailingZeros().toPlainString()
                            + ". " + dto.getSourceAccount()
                            + " is not set up. Set it up for balance and spending insights?")
                    .needFollowup(true)
                    .missingFields(List.of(SETUP_SOURCE_ACCOUNT))
                    .partial(dto)
                    .savedEntity(saved)
                    .actions(List.of(
                            new ResponseAction("answer:SETUP_SOURCE_ACCOUNT", "Set up account"),
                            new ResponseAction("answer:SKIP_SOURCE_SETUP", "Not now")))
                    .build();
        }

        ctx.reset();
        return expenseConfirmation(saved, ctx.getTimezone());
    }

    private SpeechResult handleOptionalSourceSetup(String answer, ConversationContext ctx) {
        StateChangeEntity tx = repo.findById(ctx.getActiveTransactionId())
                .orElseThrow(() -> new IllegalStateException("Transaction not found"));
        if ("SKIP_SOURCE_SETUP".equalsIgnoreCase(answer)) {
            ctx.reset();
            return expenseConfirmation(tx, ctx.getTimezone());
        }
        if (!"SETUP_SOURCE_ACCOUNT".equalsIgnoreCase(answer)) {
            return SpeechResult.invalid("Choose Set up account or Not now.");
        }

        ExpenseDto dto = (ExpenseDto) ctx.getPartialObject();
        String type = normalizeSourceType(dto.getSourceAccount());
        StateContainerEntity source = stateContainerService.createProvisional(
                ctx.getUserId(), type, specificAccountName(dto.getSourceAccount()));
        tx.setSourceContainerId(source.getId());
        tx.setNeedsEnrichment(true);
        repo.save(tx);
        return accountInitializationFollowup(tx, dto, ctx);
    }

    private String displayValue(String value) {
        return value == null || value.isBlank() ? "null" : value;
    }

    private String specificAccountName(String source) {
        if (source == null) return null;
        String compact = normalizeAccountName(source).toUpperCase(Locale.ROOT);
        return Set.of("UPI", "BANK", "BANKUPI", "BANKACCOUNT", "CARD", "CREDIT", "CREDITCARD",
                "CASH", "WALLET").contains(compact) ? null : source;
    }

    static String specificSourceAccount(ExpenseDto dto) {
        String source = dto.getSourceAccount();
        if (source == null || source.isBlank()) return source;
        String type = normalizeSourceType(source);
        Pattern pattern = switch (type) {
            case "CREDIT_CARD" -> NAMED_CREDIT_CARD;
            case "BANK_ACCOUNT" -> NAMED_BANK_ACCOUNT;
            default -> null;
        };
        if (pattern == null || dto.getRawText() == null) return source;
        Matcher matcher = pattern.matcher(dto.getRawText());
        if (!matcher.find()) return source;
        String detected = matcher.group(1).trim();
        String cleaned;
        do {
            cleaned = detected;
            detected = detected.replaceFirst("(?i)^(?:paid|via|using|from|on|with|my|the)\\s+", "");
        } while (!detected.equals(cleaned));
        return detected;
    }

    private SpeechResult expenseConfirmation(StateChangeEntity expense, String timezone) {
        String category = expense.getCategory();
        boolean hasCategory = category != null && !category.isBlank()
                && !java.util.Set.of("null", "none", "n/a", "unknown").contains(category.trim().toLowerCase());
        String message = "Added ₹" + expense.getAmount().stripTrailingZeros().toPlainString()
                + (hasCategory ? " for " + category + "." : " expense.");
        if (!expense.isFinanciallyApplied()) {
            message += " It is saved for spending insights; exact account balance is not updated yet.";
        }
        message += budgetInsights.alert(expense, timezone).map(alert -> " " + alert).orElse("");
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
