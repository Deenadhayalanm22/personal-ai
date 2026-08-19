package com.apps.deen_sa.conversation.interpretation;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.ResponseAction;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.conversation.ConversationMessages;
import com.apps.deen_sa.conversation.UnprocessedConversationService;
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
    private static final Set<String> HELP_COMMANDS = Set.of("hi", "hello", "hey", "help", "/start", "வணக்கம்", "உதவி");

    private final ConversationInterpreter interpreter;
    private final ExtensionCatalog extensions;
    private final MutationAuthorizationPolicy mutationPolicy;
    private final ConversationMessages messages;
    private final UnprocessedConversationService unprocessed;

    public UnifiedConversationEngine(ConversationInterpreter interpreter, ExtensionCatalog extensions,
                                     MutationAuthorizationPolicy mutationPolicy, ConversationMessages messages,
                                     UnprocessedConversationService unprocessed) {
        this.interpreter = interpreter;
        this.extensions = extensions;
        this.mutationPolicy = mutationPolicy;
        this.messages = messages;
        this.unprocessed = unprocessed;
    }

    public SpeechResult process(String text, ConversationContext context) {
        SpeechResult deterministic = deterministicTurn(text, context);
        if (deterministic != null) return finishDeterministicTurn(text, deterministic, context);

        String deterministicQuery = extensions.queryDeterministically(tenantId(context), text).orElse(null);
        if (deterministicQuery != null) {
            QueryPeriod query;
            try { query = QueryPeriod.valueOf(deterministicQuery); }
            catch (IllegalArgumentException invalid) { query = QueryPeriod.NONE; }
            if (query != QueryPeriod.NONE) {
                String queryName = query.name();
                SpeechResult result = extensions.query(tenantId(context), "QUERY")
                        .map(capability -> toSpeechResult(capability.handle(queryName, text, context)))
                        .orElseGet(() -> SpeechResult.unknown("That query capability is not enabled for this business."));
                return finishDeterministicTurn(text, result, context);
            }
        }

        List<com.apps.deen_sa.extension.api.DeterministicEventCandidate> extracted =
                extensions.extractDeterministically(tenantId(context), text);
        if (!extracted.isEmpty()) {
            List<EventPatch> events = extracted.stream().map(candidate -> new EventPatch(null, candidate.eventType(),
                    candidate.fields(), List.of(), List.of(), candidate.fields().entrySet().stream()
                    .map(field -> new FieldEvidence(field.getKey(), String.valueOf(field.getValue()),
                            evidenceFor(field.getValue(), text), 1.0)).toList())).toList();
            TurnInterpretation turn = new TurnInterpretation(events.size() == 1 ? TurnType.NEW_EVENT : TurnType.NEW_EVENTS,
                    events.getFirst().eventType(), context.getLocale(), null, events, null, QueryPeriod.NONE, List.of(), 1.0);
            if (!mutationPolicy.isAuthorized(turn, text)) {
                unprocessed.record(text, "MUTATION_NOT_GROUNDED", context);
                return finishDeterministicTurn(text, SpeechResult.info(messages.mutationNeedsClarification(context.getLocale())), context);
            }
            return finishDeterministicTurn(text, execute(turn, text, context), context);
        }

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
        TurnInterpretation turn = scopePendingTurn(interpreter.interpret(text, input), context);
        String pendingFieldType = context.isInFollowup()
                ? extensions.event(tenantId(context), context.getActiveIntent())
                        .map(capability -> capability.fieldTypes().get(context.getWaitingForField())).orElse(null)
                : null;
        turn = recoverPendingTextAnswer(turn, text, context, pendingFieldType);
        validate(turn, context);
        applyTurnLanguage(turn, context);
        if (!mutationPolicy.isAuthorized(turn, text)) {
            unprocessed.record(text, "MUTATION_NOT_GROUNDED", context);
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
        if (turn.turnType() == TurnType.COMMAND) return command(turn.command(), text, context);
        if (context.isInFollowup() && (turn.turnType() == TurnType.AMBIGUOUS
                || turn.events().isEmpty() && turn.turnType() != TurnType.QUERY && turn.turnType() != TurnType.COMMAND)) {
            unprocessed.record(text, "UNRECOGNIZED_FOLLOWUP", context);
            String question = context.getLastQuestion() == null
                    ? messages.mutationNeedsClarification(context.getLocale()) : context.getLastQuestion();
            return SpeechResult.followup(question, List.of(context.getWaitingForField()), context.getPartialObject());
        }
        if (turn.turnType() == TurnType.AMBIGUOUS || turn.events().isEmpty() && turn.turnType() != TurnType.QUERY) {
            return unresolved(text, "AMBIGUOUS_OR_UNSUPPORTED", context);
        }
        if (turn.turnType() == TurnType.QUERY) {
            if (turn.query() != null && turn.query() != QueryPeriod.NONE) {
                return extensions.query(tenantId(context), "QUERY")
                        .map(query -> toSpeechResult(query.handle(turn.query().name(), text,
                                turn.analysisIntent(), turn.presentationMood(), context)))
                        .orElseGet(() -> SpeechResult.unknown("That query capability is not enabled for this business."));
            }
            SpeechResult result = extensions.query(tenantId(context), "QUERY")
                    .map(query -> toSpeechResult(query.handle(QueryPeriod.LAST_7_DAYS.name(), text,
                            turn.analysisIntent(), turn.presentationMood(), context)))
                    .orElseGet(() -> SpeechResult.unknown("That query capability is not enabled for this business."));
            if (result.getMessage() != null && !result.getMessage().isBlank()) {
                result.setMessage(result.getMessage() + "\n\n" + messages.defaultQueryPeriodGuidance(context.getLocale()));
            }
            return result;
        }

        List<SpeechResult> results = new ArrayList<>();
        for (EventPatch event : turn.events()) {
            boolean continuation = context.isInFollowup() && (
                    turn.turnType() == TurnType.ANSWER_TO_PENDING_EVENT
                            || turn.turnType() == TurnType.CORRECTION
                            || answersPendingField(event, context.getWaitingForField()));
            EventPatch scopedEvent = continuation ? scopeToPendingField(event, context.getWaitingForField()) : event;
            results.add(extensions.event(tenantId(context), scopedEvent.eventType())
                    .map(capability -> toSpeechResult(capability.handle(scopedEvent, text, context, continuation)))
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

    static EventPatch scopeToPendingField(EventPatch event, String waitingForField) {
        if (event == null || waitingForField == null) return event;
        String field = "spentAt".equals(waitingForField) ? "transactionDate" : waitingForField;
        Object value = event.fields().asMap().get(field);
        Map<String, Object> fields = value == null ? Map.of() : Map.of(field, value);
        List<FieldEvidence> evidence = event.evidence().stream()
                .filter(item -> item != null && (field.equals(item.field()) || waitingForField.equals(item.field())))
                .toList();
        return new EventPatch(event.eventId(), event.eventType(), fields, event.unresolvedFields(),
                event.ambiguities(), evidence);
    }

    static TurnInterpretation scopePendingTurn(TurnInterpretation turn, ConversationContext context) {
        if (turn == null || context == null || !context.isInFollowup() || turn.events().isEmpty()
                || turn.turnType() == TurnType.QUERY || turn.turnType() == TurnType.COMMAND) return turn;
        if ((turn.turnType() == TurnType.NEW_EVENT || turn.turnType() == TurnType.NEW_EVENTS)
                && turn.events().stream().anyMatch(UnifiedConversationEngine::hasCurrentMessageAmountEvidence)) {
            context.reset();
            return turn;
        }
        List<EventPatch> events = turn.events().stream()
                .filter(event -> context.getActiveIntent().equalsIgnoreCase(event.eventType()))
                .map(event -> scopeToPendingField(event, context.getWaitingForField()))
                .toList();
        if (events.isEmpty()) return turn;
        return new TurnInterpretation(TurnType.ANSWER_TO_PENDING_EVENT, context.getActiveIntent(), turn.language(),
                turn.targetEventId(), events, turn.command(), turn.query(), turn.analysisIntent(),
                turn.presentationMood(), turn.ambiguities(), turn.confidence());
    }

    private static boolean hasCurrentMessageAmountEvidence(EventPatch event) {
        if (event == null || !event.fields().asMap().containsKey("amount")) return false;
        return event.evidence().stream().anyMatch(item -> item != null && "amount".equals(item.field())
                && item.evidence() != null && !item.evidence().isBlank());
    }

    static TurnInterpretation recoverPendingTextAnswer(TurnInterpretation turn, String text,
                                                       ConversationContext context, String fieldType) {
        if (turn == null || context == null || !context.isInFollowup() || !"string".equals(fieldType)
                || text == null || text.isBlank() || text.contains("?")
                || turn.turnType() == TurnType.QUERY || turn.turnType() == TurnType.COMMAND) return turn;
        String waitingForField = context.getWaitingForField();
        String field = "spentAt".equals(waitingForField) ? "transactionDate" : waitingForField;
        if ("transactionDate".equals(field) || "rawText".equals(field)) return turn;
        boolean alreadyExtracted = turn.events().stream()
                .filter(event -> context.getActiveIntent().equalsIgnoreCase(event.eventType()))
                .anyMatch(event -> event.fields().asMap().containsKey(field));
        if (alreadyExtracted) return turn;
        EventPatch fallback = new EventPatch(null, context.getActiveIntent(), Map.of(field, text.trim()),
                List.of(), List.of(), List.of(new FieldEvidence(field, text.trim(), text, 1.0)));
        return new TurnInterpretation(TurnType.ANSWER_TO_PENDING_EVENT, context.getActiveIntent(), turn.language(),
                turn.targetEventId(), List.of(fallback), null, QueryPeriod.NONE, turn.ambiguities(), 1.0);
    }

    private SpeechResult command(String command, String originalText, ConversationContext context) {
        if (command != null && Set.of("HELP", "GREETING").contains(command.toUpperCase(Locale.ROOT))) {
            return isHelpRequest(originalText) ? SpeechResult.info(help(context))
                    : unresolved(originalText, "UNGROUNDED_HELP_COMMAND", context);
        }
        if ("SKIP_PENDING".equals(command) && context.isInFollowup()) {
            context.reset();
            return SpeechResult.info(messages.skipped(context.getLocale()));
        }
        if ("CANCEL_PENDING".equals(command) && context.isInFollowup()) {
            context.reset();
            return SpeechResult.info(messages.cancelled(context.getLocale()));
        }
        // A model cannot invent a skip/cancel operation when no follow-up is active. Greetings, help,
        // and explicit pending controls are handled deterministically; all other commands enter review.
        return unresolved(originalText, "UNKNOWN_COMMAND", context);
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
            return SpeechResult.info(help(context));
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

    private String help(ConversationContext context) {
        String value = extensions.help(tenantId(context), context.getLocale());
        return value == null || value.isBlank() ? messages.gettingStarted(context.getLocale()) : value;
    }

    static boolean isHelpRequest(String text) {
        if (text == null) return false;
        String normalized = text.trim().toLowerCase(Locale.ROOT).replaceAll("[.!?]+$", "").trim();
        if (HELP_COMMANDS.contains(normalized)) return true;
        return normalized.matches("(?:please\\s+)?(?:show|tell)\\s+me\\s+(?:help|what you can do)")
                || normalized.matches("(?:can|could)\\s+you\\s+help(?:\\s+me)?")
                || normalized.matches("what\\s+can\\s+you\\s+do");
    }

    private SpeechResult unresolved(String text, String reason, ConversationContext context) {
        unprocessed.record(text, reason, context);
        return SpeechResult.info(messages.unprocessed(context.getLocale()));
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

    private String evidenceFor(Object value, String text) {
        if (value == null || text == null) return "";
        String rendered = String.valueOf(value);
        return text.contains(rendered) ? rendered : text;
    }

    private SpeechResult toSpeechResult(com.apps.deen_sa.extension.api.CapabilityResult result) {
        SpeechStatus status;
        try { status = SpeechStatus.valueOf(result.status()); }
        catch (Exception ignored) { status = SpeechStatus.UNKNOWN; }
        return SpeechResult.builder().status(status).message(result.message()).needFollowup(result.followup())
                .missingFields(result.missingFields()).partial(result.partial()).savedEntity(result.savedEntity())
                .actions(result.actions().stream().map(value -> new ResponseAction(value.id(), value.title())).toList())
                .media(result.media()).build();
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
