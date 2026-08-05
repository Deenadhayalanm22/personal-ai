package com.apps.deen_sa.conversation.interpretation;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.ResponseAction;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.state.StateContainerService;
import com.apps.deen_sa.finance.expense.ExpenseHandler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UnifiedConversationEngine {
    private static final int HISTORY_LIMIT = 12;
    private static final String VERSION = "unified-v1";

    private final ConversationInterpreter interpreter;
    private final ExpenseHandler expenseHandler;
    private final StateContainerService containerService;
    private final Map<String, SpeechHandler> handlers;

    public UnifiedConversationEngine(ConversationInterpreter interpreter, ExpenseHandler expenseHandler,
                                     StateContainerService containerService, List<SpeechHandler> handlers) {
        this.interpreter = interpreter;
        this.expenseHandler = expenseHandler;
        this.containerService = containerService;
        this.handlers = handlers.stream().collect(Collectors.toMap(SpeechHandler::intentType, handler -> handler));
    }

    public SpeechResult process(String text, ConversationContext context) {
        InterpretationContext input = new InterpretationContext(
                context.getUserId(), context.getTimezone(), "INR", context.getLastQuestion(),
                context.getPendingEvents(), context.getRecentTurns(), accountContext(context.getUserId()));
        TurnInterpretation turn = interpreter.interpret(text, input);
        validate(turn);

        SpeechResult result = execute(turn, text, context);
        appendTurn(context, "user", text);
        if (result.getMessage() != null) appendTurn(context, "assistant", result.getMessage());
        context.setLastQuestion(Boolean.TRUE.equals(result.getNeedFollowup()) ? result.getMessage() : null);
        context.setInterpreterVersion(VERSION);
        syncPendingState(context, turn);
        return result;
    }

    /** Button answers are already structured user input and must not be reinterpreted by a model. */
    public SpeechResult processTrustedAnswer(String answer, ConversationContext context) {
        if (!context.isInFollowup()) return process(answer, context);
        String field = context.getWaitingForField();
        Map<String, Object> values = "UNKNOWN_DUE_DAY".equals(answer) ? Map.of()
                : Map.of(field.equals("spentAt") ? "transactionDate" : field, answer);
        EventPatch patch = new EventPatch(null, context.getActiveIntent(), values, List.of(), List.of(),
                List.of(new FieldEvidence(field, answer, answer, 1.0)));
        TurnInterpretation turn = new TurnInterpretation(TurnType.ANSWER_TO_PENDING_EVENT,
                context.getActiveIntent(), null, List.of(patch), null, null, List.of(), 1.0);
        validate(turn);
        SpeechResult result = execute(turn, answer, context);
        appendTurn(context, "user", answer);
        if (result.getMessage() != null) appendTurn(context, "assistant", result.getMessage());
        context.setLastQuestion(Boolean.TRUE.equals(result.getNeedFollowup()) ? result.getMessage() : null);
        context.setInterpreterVersion(VERSION);
        syncPendingState(context, turn);
        return result;
    }

    private SpeechResult execute(TurnInterpretation turn, String text, ConversationContext context) {
        if (turn.turnType() == TurnType.COMMAND) return command(turn.command(), context);
        if (turn.turnType() == TurnType.AMBIGUOUS || turn.events().isEmpty() && turn.turnType() != TurnType.QUERY) {
            return SpeechResult.followup("I’m not fully sure what you want me to record. Could you say it another way?",
                    List.of("clarification"), null);
        }
        if (turn.turnType() == TurnType.QUERY) {
            SpeechHandler query = handlers.get("QUERY");
            return query == null ? SpeechResult.unknown("I understood this as a question, but queries are not available yet.")
                    : query.handleSpeech(text, context);
        }

        List<SpeechResult> results = new ArrayList<>();
        for (EventPatch event : turn.events()) {
            if (!"EXPENSE".equalsIgnoreCase(event.eventType())) {
                SpeechHandler handler = handlers.get(event.eventType());
                results.add(handler == null ? SpeechResult.unknown("I understood " + event.eventType()
                        + ", but cannot safely save it yet.") : handler.handleSpeech(text, context));
                continue;
            }
            boolean continuation = context.isInFollowup() && (
                    turn.turnType() == TurnType.ANSWER_TO_PENDING_EVENT
                            || turn.turnType() == TurnType.CORRECTION
                            || answersPendingField(event, context.getWaitingForField()));
            results.add(continuation
                    ? expenseHandler.handleInterpretedFollowup(event, text, context)
                    : expenseHandler.handleInterpreted(event, text, context));
        }
        if (results.size() == 1) return results.getFirst();
        String message = results.stream().map(SpeechResult::getMessage).filter(java.util.Objects::nonNull)
                .collect(Collectors.joining("\n"));
        return SpeechResult.info(message);
    }

    private boolean answersPendingField(EventPatch event, String waitingForField) {
        EventFields fields = event.fields();
        return switch (waitingForField) {
            case "category" -> fields.category() != null || fields.subcategory() != null;
            case "sourceAccount" -> fields.sourceAccount() != null;
            case "sourceBalance" -> fields.sourceBalance() != null;
            case "creditLimit" -> fields.creditLimit() != null;
            case "creditCardDueDay" -> fields.creditCardDueDay() != null;
            case "amount" -> fields.amount() != null;
            case "spentAt", "transactionDate" -> fields.transactionDate() != null;
            default -> false;
        };
    }

    private SpeechResult command(String command, ConversationContext context) {
        if ("SKIP_PENDING".equals(command)) {
            context.reset();
            return SpeechResult.info("No problem — I saved what you told me. You can add the missing detail later.");
        }
        if ("CANCEL_PENDING".equals(command)) {
            context.reset();
            return SpeechResult.info("Okay — I stopped the questions. Any activity already recorded is still saved.");
        }
        return SpeechResult.invalid("I understood the command, but it is not safe to apply yet.");
    }

    private void validate(TurnInterpretation turn) {
        if (turn == null || turn.turnType() == null) throw new IllegalArgumentException("Interpreter omitted turnType");
        for (EventPatch event : turn.events()) {
            Object amount = event.fields().amount();
            if (amount != null) {
                try {
                    if (new java.math.BigDecimal(amount.toString().replace(",", "")).signum() <= 0)
                        throw new IllegalArgumentException("Amount must be positive");
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("Interpreter returned a non-numeric amount", invalid);
                }
            }
        }
    }

    private List<Map<String, Object>> accountContext(Long userId) {
        return containerService.getActiveContainers(userId).stream().map(this::account).toList();
    }

    private Map<String, Object> account(StateContainerEntity account) {
        return Map.of("id", account.getId(), "name", account.getName(), "type", account.getContainerType(),
                "balanceKnown", account.getCurrentValue() != null);
    }

    private void appendTurn(ConversationContext context, String role, String text) {
        context.getRecentTurns().add(new ConversationTurn(role, text, Instant.now()));
        if (context.getRecentTurns().size() > HISTORY_LIMIT) {
            context.setRecentTurns(new ArrayList<>(context.getRecentTurns()
                    .subList(context.getRecentTurns().size() - HISTORY_LIMIT, context.getRecentTurns().size())));
        }
    }

    private void syncPendingState(ConversationContext context, TurnInterpretation turn) {
        if (!context.isInFollowup()) {
            context.getPendingEvents().clear();
            return;
        }
        EventPatch patch = turn.events().isEmpty() ? null : turn.events().getLast();
        Map<String, Object> facts = patch == null ? Map.of() : patch.fields().asMap();
        context.setPendingEvents(List.of(new PendingEvent(
                patch != null && patch.eventId() != null ? patch.eventId() : UUID.randomUUID().toString(),
                context.getActiveIntent(), context.getActiveTransactionId(), facts,
                List.of(context.getWaitingForField()), patch == null ? List.of() : patch.ambiguities(),
                patch == null ? List.of() : patch.evidence())));
    }
}
