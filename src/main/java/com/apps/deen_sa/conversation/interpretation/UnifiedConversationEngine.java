package com.apps.deen_sa.conversation.interpretation;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.ResponseAction;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.ConversationMessages;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.state.StateContainerService;
import com.apps.deen_sa.finance.expense.ExpenseHandler;
import com.apps.deen_sa.finance.expense.HumanAmountParser;
import com.apps.deen_sa.finance.query.QueryHandler;
import com.apps.deen_sa.llm.AiCallTelemetry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

@Service
public class UnifiedConversationEngine {
    private static final int HISTORY_LIMIT = 4;
    private static final String VERSION = "unified-v2";
    private static final Set<String> HELP_COMMANDS = Set.of("hi", "hello", "hey", "help", "வணக்கம்", "உதவி");

    private final ConversationInterpreter interpreter;
    private final ExpenseHandler expenseHandler;
    private final StateContainerService containerService;
    private final Map<String, SpeechHandler> handlers;
    private final MutationAuthorizationPolicy mutationPolicy;
    private final ConversationMessages messages;

    public UnifiedConversationEngine(ConversationInterpreter interpreter, ExpenseHandler expenseHandler,
                                     StateContainerService containerService, List<SpeechHandler> handlers,
                                     MutationAuthorizationPolicy mutationPolicy, ConversationMessages messages) {
        this.interpreter = interpreter;
        this.expenseHandler = expenseHandler;
        this.containerService = containerService;
        this.handlers = handlers.stream().collect(Collectors.toMap(SpeechHandler::intentType, handler -> handler));
        this.mutationPolicy = mutationPolicy;
        this.messages = messages;
    }

    public SpeechResult process(String text, ConversationContext context) {
        SpeechResult deterministic = deterministicTurn(text, context);
        if (deterministic != null) return finishDeterministicTurn(text, deterministic, context);

        InterpretationContext input = new InterpretationContext(
                context.getUserId(), context.getTimezone(), context.getCurrency(), context.getLastQuestion(),
                context.getPendingEvents(), context.getRecentTurns(), accountContext(context.getUserId()));
        TurnInterpretation turn = interpreter.interpret(text, input);
        validate(turn);
        applyTurnLanguage(turn, context);
        if (!mutationPolicy.isAuthorized(turn, text)) {
            SpeechResult safeReply = SpeechResult.info(messages.mutationNeedsClarification(context.getLocale()));
            appendTurn(context, "user", text);
            appendTurn(context, "assistant", safeReply.getMessage());
            context.setInterpreterVersion(VERSION);
            return safeReply;
        }

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
                context.getActiveIntent(), context.getLocale(), null, List.of(patch), null, QueryPeriod.NONE, List.of(), 1.0);
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
        if (turn.turnType() == TurnType.AMBIGUOUS && context.isInFollowup()) {
            String question = context.getLastQuestion() == null
                    ? messages.mutationNeedsClarification(context.getLocale()) : context.getLastQuestion();
            return SpeechResult.followup(question, List.of(context.getWaitingForField()), context.getPartialObject());
        }
        if (turn.turnType() == TurnType.AMBIGUOUS || turn.events().isEmpty() && turn.turnType() != TurnType.QUERY) {
            return SpeechResult.info(messages.gettingStarted(context.getLocale()));
        }
        if (turn.turnType() == TurnType.QUERY) {
            SpeechHandler query = handlers.get("QUERY");
            if (query instanceof QueryHandler queryHandler
                    && turn.query() != null && turn.query() != QueryPeriod.NONE) {
                return queryHandler.handleInterpreted(turn.query().name(), context);
            }
            return SpeechResult.info(messages.queryPeriodQuestion(context.getLocale()));
        }

        List<SpeechResult> results = new ArrayList<>();
        for (EventPatch event : turn.events()) {
            if (!"EXPENSE".equalsIgnoreCase(event.eventType())) {
                SpeechHandler handler = handlers.get(event.eventType());
                results.add(handler == null
                        ? SpeechResult.unknown("I understood " + event.eventType()
                                + ", but cannot safely save it yet.")
                        : handler instanceof StructuredEventHandler structured
                                ? structured.handleInterpreted(event, text, context)
                                : handler.handleSpeech(text, context));
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
            return SpeechResult.info(messages.skipped(context.getLocale()));
        }
        if ("CANCEL_PENDING".equals(command)) {
            context.reset();
            return SpeechResult.info(messages.cancelled(context.getLocale()));
        }
        // Greetings, help requests, and unknown non-mutating commands should teach the user what the app can do.
        return SpeechResult.info(messages.gettingStarted(context.getLocale()));
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

    private SpeechResult deterministicTurn(String text, ConversationContext context) {
        String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (!context.isInFollowup() && HELP_COMMANDS.contains(normalized)) {
            AiCallTelemetry.avoided("help_command");
            return SpeechResult.info(messages.gettingStarted(context.getLocale()));
        }
        if (context.isInFollowup() && Set.of("skip", "later", "not sure", "தவிர்").contains(normalized)) {
            context.reset();
            AiCallTelemetry.avoided("conversation_control");
            return SpeechResult.info(messages.skipped(context.getLocale()));
        }
        if (context.isInFollowup() && Set.of("cancel", "stop", "ரத்து").contains(normalized)) {
            context.reset();
            AiCallTelemetry.avoided("conversation_control");
            return SpeechResult.info(messages.cancelled(context.getLocale()));
        }
        if (!context.isInFollowup()) return null;
        String field = context.getWaitingForField();
        if (!Set.of("amount", "sourceBalance", "creditLimit", "creditCardDueDay").contains(field)) return null;
        BigDecimal value = HumanAmountParser.parse(text).orElse(null);
        if (value == null || value.signum() < 0) return null;
        if ("creditCardDueDay".equals(field)
                && (value.stripTrailingZeros().scale() > 0 || value.compareTo(BigDecimal.ONE) < 0
                || value.compareTo(BigDecimal.valueOf(31)) > 0)) return null;
        Object fieldValue = "creditCardDueDay".equals(field) ? value.intValue() : value;
        EventPatch patch = new EventPatch(null, context.getActiveIntent(), Map.of(field, fieldValue),
                List.of(), List.of(), List.of(new FieldEvidence(field, fieldValue.toString(), text, 1.0)));
        TurnInterpretation turn = new TurnInterpretation(TurnType.ANSWER_TO_PENDING_EVENT,
                context.getActiveIntent(), context.getLocale(), null, List.of(patch), null, QueryPeriod.NONE, List.of(), 1.0);
        AiCallTelemetry.avoided("pending_numeric_answer");
        SpeechResult result = execute(turn, text, context);
        syncPendingState(context, turn);
        return result;
    }

    private SpeechResult finishDeterministicTurn(String text, SpeechResult result, ConversationContext context) {
        appendTurn(context, "user", text);
        if (result.getMessage() != null) appendTurn(context, "assistant", result.getMessage());
        context.setLastQuestion(Boolean.TRUE.equals(result.getNeedFollowup()) ? result.getMessage() : null);
        context.setInterpreterVersion(VERSION);
        return result;
    }

    private void applyTurnLanguage(TurnInterpretation turn, ConversationContext context) {
        if (turn.language() == null) return;
        if (turn.language().equalsIgnoreCase("ta-IN")) context.setLocale("ta-IN");
        else if (turn.language().equalsIgnoreCase("en-IN")) context.setLocale("en-IN");
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
