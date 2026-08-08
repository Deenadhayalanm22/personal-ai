package com.apps.deen_sa.conversation.interpretation;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.ResponseAction;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.conversation.ConversationMessages;
import com.apps.deen_sa.extension.api.EventCapability;
import com.apps.deen_sa.extension.runtime.ExtensionCatalog;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Locale;
import java.util.Set;

@Service
public class UnifiedConversationEngine {
    private static final int HISTORY_LIMIT = 4;
    private static final String VERSION = "unified-v2";
    private static final Set<String> HELP_COMMANDS = Set.of("hi", "hello", "hey", "help", "வணக்கம்", "உதவி");

    private final ConversationInterpreter interpreter;
    private final ExtensionCatalog extensions;
    private final MutationAuthorizationPolicy mutationPolicy;
    private final ConversationMessages messages;

    public UnifiedConversationEngine(ConversationInterpreter interpreter, ExtensionCatalog extensions,
                                     MutationAuthorizationPolicy mutationPolicy, ConversationMessages messages) {
        this.interpreter = interpreter;
        this.extensions = extensions;
        this.mutationPolicy = mutationPolicy;
        this.messages = messages;
    }

    public SpeechResult process(String text, ConversationContext context) {
        SpeechResult deterministic = deterministicTurn(text, context);
        if (deterministic != null) return finishDeterministicTurn(text, deterministic, context);

        EventCapability routed = extensions.routeDeterministically(tenantId(context), text).orElse(null);
        if (routed != null) {
            EventPatch patch = new EventPatch(null, routed.eventType(), Map.of("rawText", text),
                    List.of(), List.of(), List.of(new FieldEvidence("rawText", text, text, 1.0)));
            SpeechResult result = toSpeechResult(routed.handle(patch, text, context, false));
            return finishDeterministicTurn(text, result, context);
        }

        InterpretationContext input = new InterpretationContext(
                context.getUserId(), context.getTimezone(), context.getCurrency(), context.getLastQuestion(),
                context.getPendingEvents(), context.getRecentTurns(), extensions.context(tenantId(context), context.getUserId()));
        TurnInterpretation turn = interpreter.interpret(text, input);
        validate(turn, context);
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
        validate(turn, context);
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
            if (turn.query() != null && turn.query() != QueryPeriod.NONE) {
                return extensions.query(tenantId(context), "QUERY")
                        .map(query -> toSpeechResult(query.handle(turn.query().name(), context)))
                        .orElseGet(() -> SpeechResult.unknown("That query capability is not enabled for this business."));
            }
            return SpeechResult.info(messages.queryPeriodQuestion(context.getLocale()));
        }

        List<SpeechResult> results = new ArrayList<>();
        for (EventPatch event : turn.events()) {
            boolean continuation = context.isInFollowup() && (
                    turn.turnType() == TurnType.ANSWER_TO_PENDING_EVENT
                            || turn.turnType() == TurnType.CORRECTION
                            || answersPendingField(event, context.getWaitingForField()));
            results.add(extensions.event(tenantId(context), event.eventType())
                    .map(capability -> toSpeechResult(capability.handle(event, text, context, continuation)))
                    .orElseGet(() -> SpeechResult.unknown("I understood " + event.eventType()
                            + ", but that capability is not enabled for this business.")));
        }
        if (results.size() == 1) return results.getFirst();
        String message = results.stream().map(SpeechResult::getMessage).filter(java.util.Objects::nonNull)
                .collect(Collectors.joining("\n"));
        return SpeechResult.info(message);
    }

    private boolean answersPendingField(EventPatch event, String waitingForField) {
        if (waitingForField == null) return false;
        Map<String, Object> facts = event.fields().asMap();
        if ("spentAt".equals(waitingForField)) return facts.containsKey("transactionDate");
        return facts.containsKey(waitingForField);
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

    private void validate(TurnInterpretation turn, ConversationContext context) {
        if (turn == null || turn.turnType() == null) throw new IllegalArgumentException("Interpreter omitted turnType");
        for (EventPatch event : turn.events()) {
            EventCapability capability = extensions.event(tenantId(context), event.eventType()).orElse(null);
            if (capability == null) continue;
            capability.fieldTypes().forEach((field, type) -> {
                Object value = event.fields().asMap().get(field);
                if (value != null && "number".equals(type) && !(value instanceof Number))
                    throw new IllegalArgumentException("Interpreter returned a non-numeric value for " + field);
            });
        }
    }

    private SpeechResult deterministicTurn(String text, ConversationContext context) {
        String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (!context.isInFollowup() && HELP_COMMANDS.contains(normalized)) {
            return SpeechResult.info(messages.gettingStarted(context.getLocale()));
        }
        if (context.isInFollowup() && Set.of("skip", "later", "not sure", "தவிர்").contains(normalized)) {
            context.reset();
            return SpeechResult.info(messages.skipped(context.getLocale()));
        }
        if (context.isInFollowup() && Set.of("cancel", "stop", "ரத்து").contains(normalized)) {
            context.reset();
            return SpeechResult.info(messages.cancelled(context.getLocale()));
        }
        if (!context.isInFollowup()) return null;
        String field = context.getWaitingForField();
        EventCapability capability = extensions.event(tenantId(context), context.getActiveIntent()).orElse(null);
        if (capability == null || !"number".equals(capability.fieldTypes().get(field))) return null;
        java.math.BigDecimal value = parsePositiveDecimal(text);
        if (value == null || value.signum() < 0) return null;
        EventPatch patch = new EventPatch(null, context.getActiveIntent(), Map.of(field, value),
                List.of(), List.of(), List.of(new FieldEvidence(field, value.toString(), text, 1.0)));
        TurnInterpretation turn = new TurnInterpretation(TurnType.ANSWER_TO_PENDING_EVENT,
                context.getActiveIntent(), context.getLocale(), null, List.of(patch), null, QueryPeriod.NONE, List.of(), 1.0);
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

    private Long tenantId(ConversationContext context) {
        Object configured = context.getMetadata() == null ? null : context.getMetadata().get("tenantId");
        if (configured instanceof Number number) return number.longValue();
        return context.getUserId() == null ? 1L : context.getUserId();
    }

    private java.math.BigDecimal parsePositiveDecimal(String text) {
        if (text == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)").matcher(text);
        if (!matcher.find()) return null;
        try { return new java.math.BigDecimal(matcher.group(1).replace(",", "")); }
        catch (NumberFormatException ignored) { return null; }
    }

    private SpeechResult toSpeechResult(com.apps.deen_sa.extension.api.CapabilityResult result) {
        SpeechStatus status;
        try { status = SpeechStatus.valueOf(result.status()); }
        catch (Exception ignored) { status = SpeechStatus.UNKNOWN; }
        return SpeechResult.builder().status(status).message(result.message()).needFollowup(result.followup())
                .missingFields(result.missingFields()).partial(result.partial()).savedEntity(result.savedEntity())
                .actions(result.actions().stream().map(value -> new ResponseAction(value.id(), value.title())).toList()).build();
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
