package com.apps.deen_sa.finance.expense.correction;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.ResponseAction;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.apps.deen_sa.conversation.interpretation.StructuredEventHandler;
import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ExpenseCorrectionHandler implements StructuredEventHandler {
    private static final String INTENT = "EXPENSE_CORRECTION";
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH);
    private final ExpenseCorrectionFinder finder;
    private final ExpenseCorrectionService corrections;
    private final StateChangeRepository transactions;
    private final StateContainerRepository containers;

    public ExpenseCorrectionHandler(ExpenseCorrectionFinder finder, ExpenseCorrectionService corrections,
                                    StateChangeRepository transactions, StateContainerRepository containers) {
        this.finder = finder;
        this.corrections = corrections;
        this.transactions = transactions;
        this.containers = containers;
    }

    @Override public String intentType() { return INTENT; }

    @Override
    public SpeechResult handleSpeech(String text, ConversationContext context) {
        return start(new ExpenseCorrectionState(), context);
    }

    @Override
    public SpeechResult handleInterpreted(EventPatch event, String rawText, ConversationContext context) {
        ExpenseCorrectionState state = new ExpenseCorrectionState();
        state.setAction(enumValue(event.fields().asMap().get("correctionAction")));
        state.setCategory(text(event.fields().asMap().get("category")));
        state.setSubcategory(text(event.fields().asMap().get("subcategory")));
        return start(state, context);
    }

    private SpeechResult start(ExpenseCorrectionState state, ConversationContext context) {
        context.setActiveIntent(INTENT);
        context.setWaitingForField("correctionChoice");
        context.setPartialObject(state);
        return browse(context, state);
    }

    @Override
    public SpeechResult handleFollowup(String answer, ConversationContext context) {
        if (!(context.getPartialObject() instanceof ExpenseCorrectionState state)) {
            context.reset();
            return SpeechResult.info("That correction request expired. Please ask to edit or delete a transaction again.");
        }
        String value = answer == null ? "" : answer.trim();
        try {
            return switch (state.getStage()) {
                case BROWSING -> browseAnswer(context, state, value);
                case CHOOSING_ACTION -> actionAnswer(context, state, value);
                case CHOOSING_FIELD -> fieldAnswer(context, state, value);
                case ENTERING_VALUE -> valueAnswer(context, state, value);
                case CONFIRMING -> confirmationAnswer(context, state, value);
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            context.reset();
            return SpeechResult.info(exception.getMessage());
        }
    }

    private SpeechResult browseAnswer(ConversationContext context, ExpenseCorrectionState state, String answer) {
        if ("OLDER".equalsIgnoreCase(answer)) return browse(context, state);
        Long selected = selectedId(state, answer);
        if (selected == null) return browse(context, state, "Choose one of the listed transactions, or select Show older.");
        StateChangeEntity expense = owned(context, selected);
        state.setSelectedTransactionId(expense.getId());
        if (state.getAction() == CorrectionAction.DELETE) return deleteConfirmation(context, state, expense);
        if (state.getAction() == CorrectionAction.EDIT) return editFields(context, state, expense);
        state.setStage(CorrectionStage.CHOOSING_ACTION);
        return followup(context, state, summary(expense, context) + "\n\nWhat would you like to do?", List.of(
                action("EDIT", "Edit"), action("DELETE", "Delete"), cancel()));
    }

    private SpeechResult actionAnswer(ConversationContext context, ExpenseCorrectionState state, String answer) {
        if ("DELETE".equalsIgnoreCase(answer)) {
            state.setAction(CorrectionAction.DELETE);
            return deleteConfirmation(context, state, owned(context, state.getSelectedTransactionId()));
        }
        if ("EDIT".equalsIgnoreCase(answer)) {
            state.setAction(CorrectionAction.EDIT);
            return editFields(context, state, owned(context, state.getSelectedTransactionId()));
        }
        return followup(context, state, "Choose Edit or Delete.", List.of(action("EDIT", "Edit"), action("DELETE", "Delete"), cancel()));
    }

    private SpeechResult editFields(ConversationContext context, ExpenseCorrectionState state, StateChangeEntity expense) {
        state.setStage(CorrectionStage.CHOOSING_FIELD);
        return followup(context, state, summary(expense, context) + "\n\nWhat do you want to change?", List.of(
                action("FIELD_AMOUNT", "Amount"), action("FIELD_CATEGORY", "Category"),
                action("FIELD_MERCHANT", "Merchant"), action("FIELD_DATE", "Date"),
                action("FIELD_ACCOUNT", "Account"), cancel()));
    }

    private SpeechResult fieldAnswer(ConversationContext context, ExpenseCorrectionState state, String answer) {
        String fieldName = answer.toUpperCase(Locale.ROOT).replace("FIELD_", "");
        try { state.setField(CorrectionField.valueOf(fieldName)); }
        catch (IllegalArgumentException invalid) { return editFields(context, state, owned(context, state.getSelectedTransactionId())); }
        state.setStage(CorrectionStage.ENTERING_VALUE);
        if (state.getField() == CorrectionField.ACCOUNT) {
            List<ResponseAction> actions = containers.findActiveByOwnerId(context.getUserId()).stream()
                    .filter(value -> Set.of("BANK_ACCOUNT", "CASH", "WALLET", "CREDIT_CARD").contains(value.getContainerType()))
                    .map(value -> action("ACCOUNT_" + value.getId(), value.getName())).toList();
            if (actions.isEmpty()) return followup(context, state, "You have no active payment accounts to choose from.", List.of(cancel()));
            return followup(context, state, "Which account should this expense use?", actions);
        }
        String question = switch (state.getField()) {
            case AMOUNT -> "What is the correct amount?";
            case CATEGORY -> "What is the correct category?";
            case MERCHANT -> "What is the correct merchant?";
            case DATE -> "What is the correct date? You can say today, yesterday, or 12 Aug 2026.";
            case ACCOUNT -> throw new IllegalStateException("Account choices were not presented.");
        };
        return followup(context, state, question, List.of(cancel()));
    }

    private SpeechResult valueAnswer(ConversationContext context, ExpenseCorrectionState state, String answer) {
        Object parsed = parseValue(state.getField(), answer, context);
        state.setProposedValue(serialize(parsed));
        state.setStage(CorrectionStage.CONFIRMING);
        StateChangeEntity original = owned(context, state.getSelectedTransactionId());
        String preview = preview(original, state.getField(), parsed, context);
        return followup(context, state, preview + "\n\nConfirm this update?", List.of(
                action("CONFIRM", "Confirm update"), cancel()));
    }

    private SpeechResult confirmationAnswer(ConversationContext context, ExpenseCorrectionState state, String answer) {
        if (!"CONFIRM".equalsIgnoreCase(answer))
            return followup(context, state, "Please confirm or cancel the correction.", List.of(action("CONFIRM", "Confirm"), cancel()));
        CorrectionOutcome outcome = state.getAction() == CorrectionAction.DELETE
                ? corrections.voidExpense(context.getUserId(), state.getSelectedTransactionId())
                : corrections.editExpense(context.getUserId(), state.getSelectedTransactionId(), state.getField(),
                        deserialize(state.getField(), state.getProposedValue(), context));
        context.reset();
        String message = outcome.deleted() ? "✓ Transaction deleted. It was voided and removed from spending reports."
                : "✓ Transaction updated to " + summary(outcome.replacement(), context) + ".";
        if (outcome.balanceImpact() != null) message += " " + outcome.balanceImpact() + " to the affected balance.";
        Object recordedEntity = outcome.replacement() == null ? outcome.original() : outcome.replacement();
        return SpeechResult.builder().status(SpeechStatus.SAVED).message(message)
                .savedEntity(recordedEntity).needFollowup(false).build();
    }

    private SpeechResult deleteConfirmation(ConversationContext context, ExpenseCorrectionState state, StateChangeEntity expense) {
        state.setStage(CorrectionStage.CONFIRMING);
        return followup(context, state, "Delete this transaction?\n\n" + summary(expense, context)
                + "\n\nIt will be voided, removed from reports, and any applied balance impact will be reversed.",
                List.of(action("CONFIRM", "Delete transaction"), cancel()));
    }

    private SpeechResult browse(ConversationContext context, ExpenseCorrectionState state) { return browse(context, state, null); }
    private SpeechResult browse(ConversationContext context, ExpenseCorrectionState state, String prefix) {
        ExpenseBrowsePage page = finder.find(context.getUserId().toString(), state);
        if (page.transactions().isEmpty()) {
            context.reset();
            return SpeechResult.info(prefix == null ? "I couldn't find any matching active expenses." : prefix);
        }
        if (page.transactions().size() == 1) {
            StateChangeEntity expense = page.transactions().getFirst();
            state.setVisibleTransactionIds(List.of(expense.getId()));
            state.setBeforeId(null);
            state.setStage(CorrectionStage.BROWSING);
            return followup(context, state, "Is this the transaction you want to " + actionVerb(state) + "?\n\n"
                    + summary(expense, context), List.of(action("SELECT_" + expense.getId(), "Yes"), cancel()));
        }
        state.setVisibleTransactionIds(page.transactions().stream().map(StateChangeEntity::getId).toList());
        state.setBeforeId(page.nextCursor());
        state.setStage(CorrectionStage.BROWSING);
        StringBuilder message = new StringBuilder(prefix == null ? "Select a transaction:" : prefix + "\n\n");
        List<ResponseAction> actions = new ArrayList<>();
        for (int index = 0; index < page.transactions().size(); index++) {
            StateChangeEntity expense = page.transactions().get(index);
            message.append("\n").append(index + 1).append(". ").append(summary(expense, context));
            actions.add(action("SELECT_" + expense.getId(), String.valueOf(index + 1)));
        }
        if (page.hasMore()) actions.add(action("OLDER", "Show older"));
        actions.add(cancel());
        return followup(context, state, message.toString(), actions);
    }

    private String actionVerb(ExpenseCorrectionState state) {
        return state.getAction() == CorrectionAction.DELETE ? "delete" : "edit";
    }

    private CorrectionAction enumValue(Object value) {
        if (value == null) return null;
        try { return CorrectionAction.valueOf(value.toString().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private Long selectedId(ExpenseCorrectionState state, String answer) {
        String normalized = answer.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("SELECT_")) {
            try { return Long.valueOf(normalized.substring("SELECT_".length())); }
            catch (NumberFormatException ignored) { return null; }
        }
        try {
            int index = Integer.parseInt(answer) - 1;
            return index >= 0 && index < state.getVisibleTransactionIds().size() ? state.getVisibleTransactionIds().get(index) : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    private Object parseValue(CorrectionField field, String answer, ConversationContext context) {
        return switch (field) {
            case AMOUNT -> {
                BigDecimal amount = new BigDecimal(answer.replaceAll("(?i)[₹,\\s]|rs\\.?|inr", ""));
                if (amount.signum() <= 0) throw new IllegalArgumentException("Please enter an amount greater than zero.");
                yield amount;
            }
            case CATEGORY, MERCHANT -> {
                if (answer.isBlank()) throw new IllegalArgumentException("The corrected value cannot be empty.");
                yield answer.trim();
            }
            case DATE -> parseDate(answer, zone(context));
            case ACCOUNT -> {
                String raw = answer.toUpperCase(Locale.ROOT).replace("ACCOUNT_", "");
                Long id;
                try { id = Long.valueOf(raw); } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Please select one of the listed accounts.");
                }
                StateContainerEntity account = containers.findById(id)
                        .filter(value -> value.getOwnerId().equals(context.getUserId()) && "ACTIVE".equals(value.getStatus()))
                        .orElseThrow(() -> new IllegalArgumentException("That account is unavailable."));
                yield account.getId();
            }
        };
    }

    private Instant parseDate(String answer, ZoneId zone) {
        String normalized = answer.trim().toLowerCase(Locale.ROOT);
        LocalDate date;
        if ("today".equals(normalized)) date = LocalDate.now(zone);
        else if ("yesterday".equals(normalized)) date = LocalDate.now(zone).minusDays(1);
        else {
            try { date = LocalDate.parse(answer.trim(), DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)); }
            catch (DateTimeParseException invalid) { throw new IllegalArgumentException("Use a date like 12 Aug 2026, today, or yesterday."); }
        }
        return date.atStartOfDay(zone).toInstant();
    }

    private String preview(StateChangeEntity value, CorrectionField field, Object replacement, ConversationContext context) {
        String oldValue = switch (field) {
            case AMOUNT -> money(value.getAmount());
            case CATEGORY -> safe(value.getCategory());
            case MERCHANT -> safe(value.getMainEntity());
            case DATE -> formatDate(value.getTimestamp(), context);
            case ACCOUNT -> accountName(value.getSourceContainerId());
        };
        String newValue = field == CorrectionField.AMOUNT ? money((BigDecimal) replacement)
                : field == CorrectionField.DATE ? formatDate((Instant) replacement, context)
                : field == CorrectionField.ACCOUNT ? accountName((Long) replacement) : replacement.toString();
        return summary(value, context) + "\n\n" + title(field) + ": " + oldValue + " → " + newValue;
    }

    private Object deserialize(CorrectionField field, String value, ConversationContext context) {
        return switch (field) {
            case AMOUNT -> new BigDecimal(value);
            case DATE -> Instant.parse(value);
            case ACCOUNT -> Long.valueOf(value);
            case CATEGORY, MERCHANT -> value;
        };
    }

    private String serialize(Object value) { return value instanceof Instant instant ? instant.toString() : value.toString(); }
    private String summary(StateChangeEntity value, ConversationContext context) {
        return money(value.getAmount()) + " · " + safe(value.getMainEntity() == null ? value.getCategory() : value.getMainEntity())
                + " · " + formatDate(value.getTimestamp(), context) + " · " + accountName(value.getSourceContainerId());
    }
    private String accountName(Long id) { return id == null ? "Account not recorded" : containers.findById(id).map(StateContainerEntity::getName).orElse("Unknown account"); }
    private String formatDate(Instant value, ConversationContext context) { return DISPLAY_DATE.withZone(zone(context)).format(value); }
    private ZoneId zone(ConversationContext context) { try { return ZoneId.of(context.getTimezone()); } catch (Exception ignored) { return ZoneId.of("UTC"); } }
    private String money(BigDecimal value) { return "₹" + value.stripTrailingZeros().toPlainString(); }
    private String safe(String value) { return value == null || value.isBlank() ? "Uncategorized" : value; }
    private String title(CorrectionField field) { String value = field.name().toLowerCase(Locale.ROOT); return Character.toUpperCase(value.charAt(0)) + value.substring(1); }
    private StateChangeEntity owned(ConversationContext context, Long id) { return transactions.findById(id)
            .filter(value -> value.getUserId().equals(context.getUserId().toString()) && value.getRecordStatus() == com.apps.deen_sa.finance.expense.ExpenseRecordStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("That transaction is unavailable. Please start again.")); }
    private SpeechResult followup(ConversationContext context, ExpenseCorrectionState state, String message, List<ResponseAction> actions) {
        context.setWaitingForField("correctionChoice"); context.setPartialObject(state);
        return SpeechResult.followup(message, List.of("correctionChoice"), state, actions);
    }
    private ResponseAction action(String answer, String title) { return new ResponseAction("answer:" + answer, title); }
    private ResponseAction cancel() { return new ResponseAction("control:cancel", "Cancel"); }
}
