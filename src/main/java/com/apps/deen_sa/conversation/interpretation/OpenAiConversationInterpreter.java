package com.apps.deen_sa.conversation.interpretation;

import com.apps.deen_sa.extension.api.EventCapability;
import com.apps.deen_sa.extension.runtime.ExtensionCatalog;
import com.apps.deen_sa.llm.AiCallTelemetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/** OpenAI adapter with a tenant-specific schema assembled only from enabled extensions. */
@Service
public class OpenAiConversationInterpreter implements ConversationInterpreter {
    private static final List<String> ANALYSIS_INTENTS = List.of("SPENDING_OVERVIEW", "CATEGORY_RANKING",
            "DAILY_PATTERN", "PERIOD_COMPARISON", "MONEY_FLOW", "ACCOUNT_OVERVIEW",
            "BUDGET_PROGRESS", "CATEGORY_HIERARCHY");
    private static final List<String> PRESENTATION_MOODS = List.of("NEUTRAL", "CURIOUS", "CONCERNED",
            "FRUSTRATED", "CELEBRATORY");
    private static final String CORE_PROMPT = """
            Interpret one multilingual operational turn into the response schema. Never invent facts, authorize a
            mutation, or apply a business rule. Every non-null fact needs exact evidence copied from the current
            message. Context may resolve a pending event but cannot create a new event by itself.

            Use only enabled event types and declared fields. Return AMBIGUOUS with no events when multiple domains
            plausibly match or evidence is insufficient. Commands are HELP, SKIP_PENDING, CANCEL_PENDING and
            UNDO_LAST. NEW_EVENT means a distinct occurrence stated now. Query period is NONE for non-query turns.

            For QUERY turns, classify the user's analytical goal semantically in any language:
            SPENDING_OVERVIEW = general spending summary;
            CATEGORY_RANKING = where the most money was spent;
            DAILY_PATTERN = spending across days;
            PERIOD_COMPARISON = comparison between time periods;
            MONEY_FLOW = where income or salary flowed;
            ACCOUNT_OVERVIEW = balances across accounts;
            BUDGET_PROGRESS = actual spending against budgets;
            CATEGORY_HIERARCHY = category/subcategory/merchant hierarchy.
            Also classify presentation mood as NEUTRAL, CURIOUS, CONCERNED, FRUSTRATED, or CELEBRATORY.
            Mood describes communication style only and must never alter facts, calculations, filters, or query scope.
            For non-query turns, use null for analysisIntent and presentationMood.

            ENABLED EXTENSION CONTRACTS:
            """;
    private final OpenAIClient client;
    private final ObjectMapper mapper;
    private final ExtensionCatalog extensions;
    private final String model;
    private final String escalationModel;
    private final double escalationConfidence;

    public OpenAiConversationInterpreter(OpenAIClient client, ObjectMapper mapper, ExtensionCatalog extensions,
            @Value("${openai.model:gpt-4.1-mini}") String model,
            @Value("${openai.escalation-model:gpt-4.1-mini}") String escalationModel,
            @Value("${openai.escalation-confidence:0.55}") double escalationConfidence) {
        this.client = client;
        this.mapper = mapper;
        this.extensions = extensions;
        this.model = model;
        this.escalationModel = escalationModel;
        this.escalationConfidence = escalationConfidence;
    }

    @Override public TurnInterpretation interpret(String userMessage, InterpretationContext context) {
        try {
            Long tenantId = context.userId() == null ? 1L : context.userId();
            Collection<EventCapability> capabilities = extensions.enabledEvents(tenantId);
            String input = mapper.writeValueAsString(Map.of("userMessage", userMessage, "context", context));
            String instructions = CORE_PROMPT + extensions.interpretationInstructions(tenantId) + pendingInstruction(context);
            EventCapability pendingCapability = pendingCapability(context, capabilities);
            if (pendingCapability != null) {
                String extractionInstructions = instructions + "\nSELECTED CAPABILITY: " + pendingCapability.eventType()
                        + "\n" + pendingCapability.extractionInstructions();
                return callModel(input, extractionInstructions, model, "conversation_pending_extraction",
                        List.of(pendingCapability));
            }
            RouteWire route = callRouter(input, instructions, model, capabilities);
            if (route.selectedEventType() == null || route.selectedEventType().isBlank()) return routeOnly(route);
            EventCapability selected = capabilities.stream()
                    .filter(value -> value.eventType().equalsIgnoreCase(route.selectedEventType())).findFirst().orElse(null);
            if (selected == null) return ambiguous(route, "Selected capability is not enabled for this tenant");
            String extractionInstructions = instructions + "\nSELECTED CAPABILITY: " + selected.eventType()
                    + "\n" + selected.extractionInstructions();
            TurnInterpretation primary = callModel(input, extractionInstructions, model,
                    "conversation_extraction", List.of(selected));
            if ((primary.confidence() == null ? 0 : primary.confidence()) < escalationConfidence
                    && escalationModel != null && !escalationModel.isBlank() && !escalationModel.equals(model)) {
                return callModel(input, extractionInstructions, escalationModel,
                        "conversation_extraction_escalation", List.of(selected));
            }
            return primary;
        } catch (Exception exception) {
            throw new ConversationInterpretationException("Unable to interpret conversation turn", exception);
        }
    }

    private RouteWire callRouter(String input, String instructions, String model,
                                 Collection<EventCapability> capabilities) {
        long started = System.nanoTime();
        try {
            ResponseFormatTextJsonSchemaConfig.Schema.Builder schema = ResponseFormatTextJsonSchemaConfig.Schema.builder();
            routingSchema(capabilities).forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
            ResponseFormatTextJsonSchemaConfig format = ResponseFormatTextJsonSchemaConfig.builder()
                    .name("extension-route").schema(schema.build()).strict(true).build();
            Response response = client.responses().create(ResponseCreateParams.builder().model(model)
                    .instructions(instructions + "\nSelect at most one enabled capability. Do not extract event facts yet.")
                    .input(input).text(ResponseTextConfig.builder().format(format).build()).build());
            response.usage().ifPresentOrElse(usage -> AiCallTelemetry.success("conversation_routing", model,
                            usage.inputTokens(), usage.inputTokensDetails().cachedTokens(), usage.outputTokens(), started),
                    () -> AiCallTelemetry.success("conversation_routing", model, 0, 0, 0, started));
            String json = response.output().stream().flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream()).flatMap(content -> content.outputText().stream())
                    .map(ResponseOutputText::text).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Router returned no output"));
            return mapper.readValue(json, RouteWire.class);
        } catch (Exception exception) {
            AiCallTelemetry.failure("conversation_routing", model, started);
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Routing model call failed", exception);
        }
    }

    private Map<String, Object> routingSchema(Collection<EventCapability> capabilities) {
        List<String> eventTypes = capabilities.stream().map(EventCapability::eventType).distinct().sorted().toList();
        return object(Map.of(
                "turnType", enumSchema(Arrays.stream(TurnType.values()).map(Enum::name).toList()),
                "selectedEventType", eventTypes.isEmpty() ? nullable("string")
                        : Map.of("anyOf", List.of(enumSchema(eventTypes), Map.of("type", "null"))),
                "language", nullable("string"), "command", nullable("string"),
                "query", enumSchema(Arrays.stream(QueryPeriod.values()).map(Enum::name).toList()),
                "analysisIntent", nullableEnum(ANALYSIS_INTENTS),
                "presentationMood", nullableEnum(PRESENTATION_MOODS),
                "ambiguities", array(string()), "confidence", nullable("number")),
                List.of("turnType", "selectedEventType", "language", "command", "query", "analysisIntent",
                        "presentationMood", "ambiguities", "confidence"));
    }

    private TurnInterpretation routeOnly(RouteWire route) {
        TurnType type = TurnType.valueOf(route.turnType());
        QueryPeriod query = route.query() == null ? QueryPeriod.NONE : QueryPeriod.valueOf(route.query());
        return new TurnInterpretation(type, route.selectedEventType(), route.language(), null, List.of(),
                route.command(), query, route.analysisIntent(), route.presentationMood(),
                route.ambiguities(), route.confidence());
    }

    private TurnInterpretation ambiguous(RouteWire route, String reason) {
        List<String> ambiguities = new ArrayList<>(route.ambiguities() == null ? List.of() : route.ambiguities());
        ambiguities.add(reason);
        return new TurnInterpretation(TurnType.AMBIGUOUS, null, route.language(), null, List.of(), null,
                QueryPeriod.NONE, null, null, ambiguities, route.confidence());
    }

    private TurnInterpretation callModel(String input, String instructions, String model, String purpose,
                                         Collection<EventCapability> capabilities) {
        long started = System.nanoTime();
        try {
            ResponseFormatTextJsonSchemaConfig.Schema.Builder schema = ResponseFormatTextJsonSchemaConfig.Schema.builder();
            responseSchema(capabilities).forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
            ResponseFormatTextJsonSchemaConfig format = ResponseFormatTextJsonSchemaConfig.builder()
                    .name("extension-turn").schema(schema.build()).strict(true).build();
            ResponseCreateParams params = ResponseCreateParams.builder().model(model).instructions(instructions).input(input)
                    .text(ResponseTextConfig.builder().format(format).build()).build();
            Response response = client.responses().create(params);
            response.usage().ifPresentOrElse(usage -> AiCallTelemetry.success(purpose, model, usage.inputTokens(),
                            usage.inputTokensDetails().cachedTokens(), usage.outputTokens(), started),
                    () -> AiCallTelemetry.success(purpose, model, 0, 0, 0, started));
            String json = response.output().stream().flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream()).flatMap(content -> content.outputText().stream())
                    .map(ResponseOutputText::text)
                    .findFirst().orElseThrow(() -> new IllegalStateException("Interpreter returned no output"));
            return fromWire(mapper.readValue(json, TurnWire.class));
        } catch (Exception exception) {
            AiCallTelemetry.failure(purpose, model, started);
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Model call failed", exception);
        }
    }

    private TurnInterpretation fromWire(TurnWire wire) {
        List<EventPatch> events = wire.events() == null ? List.of() : wire.events().stream()
                .map(value -> new EventPatch(value.eventId(), value.eventType(), value.fields(), value.unresolvedFields(),
                        value.ambiguities(), value.evidence())).toList();
        TurnType type = TurnType.valueOf(wire.turnType());
        QueryPeriod query = wire.query() == null ? QueryPeriod.NONE : QueryPeriod.valueOf(wire.query());
        return new TurnInterpretation(type, wire.intent(), wire.language(), wire.targetEventId(), events, wire.command(),
                query, wire.analysisIntent(), wire.presentationMood(), wire.ambiguities(), wire.confidence());
    }

    private Map<String, Object> responseSchema(Collection<EventCapability> capabilities) {
        Map<String, Object> fields = new TreeMap<>();
        capabilities.forEach(capability -> capability.fieldTypes().forEach((name, type) -> fields.put(name, nullable(type))));
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Map<String, Object> evidence = object(Map.of("field", string(), "value", string(), "evidence", string(),
                "confidence", nullable("number")), List.of("field", "value", "evidence", "confidence"));
        Map<String, Object> event = object(Map.of(
                "eventId", nullable("string"),
                "eventType", enumSchema(capabilities.stream().map(EventCapability::eventType).distinct().sorted().toList()),
                "fields", object(fields, fieldNames),
                "unresolvedFields", array(string()), "ambiguities", array(string()), "evidence", array(evidence)),
                List.of("eventId", "eventType", "fields", "unresolvedFields", "ambiguities", "evidence"));
        return object(Map.ofEntries(
                Map.entry("turnType", enumSchema(Arrays.stream(TurnType.values()).map(Enum::name).toList())),
                Map.entry("intent", nullable("string")), Map.entry("language", nullable("string")),
                Map.entry("targetEventId", nullable("string")), Map.entry("events", array(event)),
                Map.entry("command", nullable("string")),
                Map.entry("query", enumSchema(Arrays.stream(QueryPeriod.values()).map(Enum::name).toList())),
                Map.entry("analysisIntent", nullableEnum(ANALYSIS_INTENTS)),
                Map.entry("presentationMood", nullableEnum(PRESENTATION_MOODS)),
                Map.entry("ambiguities", array(string())), Map.entry("confidence", nullable("number"))),
                List.of("turnType", "intent", "language", "targetEventId", "events", "command", "query",
                        "analysisIntent", "presentationMood", "ambiguities", "confidence"));
    }

    private Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
    }
    private Map<String, Object> string() { return Map.of("type", "string"); }
    private Map<String, Object> array(Object items) { return Map.of("type", "array", "items", items); }
    private Map<String, Object> enumSchema(List<String> values) {
        return values.isEmpty() ? Map.of("type", "string", "enum", List.of("UNKNOWN")) : Map.of("type", "string", "enum", values);
    }
    private Map<String, Object> nullable(String type) {
        Map<String, Object> typed = "array".equals(type) ? array(string()) : Map.of("type", type);
        return Map.of("anyOf", List.of(typed, Map.of("type", "null")));
    }
    private Map<String, Object> nullableEnum(List<String> values) {
        return Map.of("anyOf", List.of(enumSchema(values), Map.of("type", "null")));
    }

    private String pendingInstruction(InterpretationContext context) {
        if (context.pendingEvents() == null || context.pendingEvents().isEmpty()) return "";
        PendingEvent pending = context.pendingEvents().getLast();
        if (pending.unresolvedFields() == null || pending.unresolvedFields().isEmpty()) return "";
        return "\nAn active " + pending.eventType() + " event is waiting for field "
                + pending.unresolvedFields().getFirst() + ". Treat a plausible direct reply as ANSWER_TO_PENDING_EVENT, "
                + "extract that field from the current message, and do not start a new event.\n";
    }

    private EventCapability pendingCapability(InterpretationContext context,
                                              Collection<EventCapability> capabilities) {
        if (context.pendingEvents() == null || context.pendingEvents().isEmpty()) return null;
        PendingEvent pending = context.pendingEvents().getLast();
        if (pending.unresolvedFields() == null || pending.unresolvedFields().isEmpty()) return null;
        return capabilities.stream().filter(capability -> capability.eventType().equalsIgnoreCase(pending.eventType()))
                .findFirst().orElse(null);
    }

    private record TurnWire(String turnType, String intent, String language, String targetEventId,
                            List<EventWire> events, String command, String query, String analysisIntent,
                            String presentationMood, List<String> ambiguities, Double confidence) { }
    private record EventWire(String eventId, String eventType, Map<String, Object> fields,
                             List<String> unresolvedFields, List<String> ambiguities, List<FieldEvidence> evidence) { }
    private record RouteWire(String turnType, String selectedEventType, String language, String command,
                             String query, String analysisIntent, String presentationMood,
                             List<String> ambiguities, Double confidence) { }
}
