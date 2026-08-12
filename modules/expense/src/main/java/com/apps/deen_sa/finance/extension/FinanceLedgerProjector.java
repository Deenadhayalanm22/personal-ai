package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.apps.deen_sa.core.ledger.GenericLedgerService;
import com.apps.deen_sa.core.ledger.CoreEventEntity;
import com.apps.deen_sa.extension.api.ExecutionPlan;
import com.apps.deen_sa.extension.api.MovementPlan;
import com.apps.deen_sa.extension.api.ObservationPlan;
import org.springframework.stereotype.Service;
import com.apps.deen_sa.finance.persistence.FinanceAnalyticsRepository;
import com.apps.deen_sa.finance.persistence.FinanceExpenseProjectionEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
class FinanceLedgerProjector {
    private final GenericLedgerService ledger;
    private final FinanceAnalyticsRepository projections;
    FinanceLedgerProjector(GenericLedgerService ledger, FinanceAnalyticsRepository projections) {
        this.ledger = ledger; this.projections = projections;
    }

    void project(EventPatch event, String rawText, ConversationContext context) {
        project(event, null, rawText, context);
    }

    void project(EventPatch event, SpeechResult result, String rawText, ConversationContext context) {
        Map<String, Object> facts = completedFacts(event.fields().asMap(), result == null ? null : result.getSavedEntity());
        String type = canonical(event.eventType());
        BigDecimal amount = decimal(facts.get("amount"));
        List<MovementPlan> movements = amount == null ? List.of() : movements(type, amount, facts);
        List<ObservationPlan> observations = observations(type, facts);
        Long tenantId = tenantId(context);
        String idempotency = idempotencyKey(type, rawText, facts, context);
        CoreEventEntity committed = ledger.commit(new ExecutionPlan(tenantId, "personal-finance", type, "1.0.0", Instant.now(),
                "user:" + context.getUserId(), facts, evidence(event), "finance-rules-v1", idempotency,
                causation(context), movements, observations));
        if ("EXPENSE".equals(type) && amount != null && !projections.existsByCoreEventId(committed.getId())) {
            FinanceExpenseProjectionEntity projection = new FinanceExpenseProjectionEntity();
            projection.setCoreEventId(committed.getId()); projection.setTenantId(tenantId);
            projection.setUserId(String.valueOf(context.getUserId())); projection.setAmount(amount);
            projection.setCategory(string(facts.get("category"))); projection.setSubcategory(string(facts.get("subcategory")));
            projection.setSourceAccount(string(facts.get("sourceAccount"))); projection.setOccurredAt(committed.getOccurredAt());
            projection.setLegacyTransactionId(result != null && result.getSavedEntity() instanceof StateChangeEntity saved
                    ? saved.getId() : null);
            projections.save(projection);
        }
    }

    private Map<String, Object> completedFacts(Map<String, Object> proposed, Object savedEntity) {
        Map<String, Object> facts = new LinkedHashMap<>(proposed);
        if (savedEntity instanceof StateChangeEntity saved) {
            put(facts, "transactionId", saved.getId());
            put(facts, "amount", saved.getAmount());
            put(facts, "category", saved.getCategory());
            put(facts, "subcategory", saved.getSubcategory());
            put(facts, "merchantName", saved.getMainEntity());
            put(facts, "transactionDate", saved.getTimestamp());
            put(facts, "rawText", saved.getRawText());
        }
        return Map.copyOf(facts);
    }

    private void put(Map<String, Object> facts, String key, Object value) {
        if (value != null) facts.put(key, value);
    }

    private BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString().replace(",", ""));
    }
    private String string(Object value) { return value == null ? null : value.toString(); }

    private List<MovementPlan> movements(String type, BigDecimal amount, Map<String, Object> facts) {
        String source = container(facts.get("sourceAccount"), "unallocated-source");
        String target = container(facts.get("destinationAccount"), "unallocated-target");
        return switch (type) {
            case "EXPENSE" -> pair("INR", source, amount.negate(),
                    "expense:" + slug(facts.getOrDefault("category", "uncategorized")), amount);
            case "INCOME" -> pair("INR", "income-source", amount.negate(), target, amount);
            case "TRANSFER" -> pair("INR", source, amount.negate(), target, amount);
            case "LIABILITY_PAYMENT" -> pair("INR", source, amount.negate(),
                    "liability-settlement", amount);
            case "ASSET_BUY" -> pair("INR", source, amount.negate(), "asset-acquisition", amount);
            case "ASSET_SELL" -> pair("INR", "asset-disposal", amount.negate(), target, amount);
            default -> List.of();
        };
    }

    private List<MovementPlan> pair(String resource, String from, BigDecimal debit, String to, BigDecimal credit) {
        return List.of(new MovementPlan(resource, from, debit, "INR"),
                new MovementPlan(resource, to, credit, "INR"));
    }

    private List<ObservationPlan> observations(String type, Map<String, Object> facts) {
        if (!"ACCOUNT_SETUP".equals(type)) return List.of();
        String account = container(facts.get("sourceAccount"), "declared-account");
        List<ObservationPlan> values = new ArrayList<>();
        addObservation(values, account, "balance", facts.get("sourceBalance"));
        addObservation(values, account, "credit-limit", facts.get("creditLimit"));
        return List.copyOf(values);
    }

    private void addObservation(List<ObservationPlan> values, String subject, String kind, Object raw) {
        if (raw == null) return;
        values.add(new ObservationPlan(kind, subject, new BigDecimal(raw.toString()), "INR", Instant.now()));
    }

    private Map<String, Object> evidence(EventPatch event) {
        Map<String, Object> values = new LinkedHashMap<>();
        event.evidence().forEach(item -> values.put(item.field(), Map.of("value", item.value(),
                "evidence", item.evidence(), "confidence", item.confidence())));
        return values;
    }

    private String canonical(String type) {
        return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            case "INVESTMENT" -> "ASSET_BUY";
            case "INVESTMENT_SELL" -> "ASSET_SELL";
            default -> type == null ? "UNKNOWN" : type.toUpperCase(Locale.ROOT);
        };
    }
    private Long tenantId(ConversationContext context) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get("tenantId");
        return value instanceof Number number ? number.longValue() : context.getUserId();
    }
    private String causation(ConversationContext context) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get("inboundMessageId");
        return value == null ? null : value.toString();
    }
    private String idempotencyKey(String type, String rawText, Map<String, Object> facts,
                                  ConversationContext context) {
        String factFingerprint = UUID.nameUUIDFromBytes(canonicalFacts(facts).getBytes(StandardCharsets.UTF_8)).toString();
        String causation = causation(context);
        if (causation != null) return "message:" + causation + ":" + type + ":" + factFingerprint;
        return "legacy:" + context.getUserId() + ":" + context.getSessionId() + ":" + type + ":"
                + Integer.toUnsignedString(Objects.hashCode(rawText)) + ":" + factFingerprint;
    }
    private String canonicalFacts(Map<String, Object> facts) {
        StringJoiner values = new StringJoiner("|");
        new TreeMap<>(facts).forEach((key, value) -> values.add(key + "=" + canonicalValue(value)));
        return values.toString();
    }
    private String canonicalValue(Object value) {
        if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros().toPlainString();
        if (value instanceof Map<?, ?> map) {
            StringJoiner values = new StringJoiner(",", "{", "}");
            map.entrySet().stream().sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> values.add(entry.getKey() + "=" + canonicalValue(entry.getValue())));
            return values.toString();
        }
        if (value instanceof Collection<?> collection)
            return collection.stream().map(this::canonicalValue).toList().toString();
        return String.valueOf(value);
    }
    private String container(Object raw, String fallback) { return raw == null ? fallback : "account:" + slug(raw); }
    private String slug(Object raw) { return raw.toString().toLowerCase(Locale.ROOT).trim().replaceAll("[^\\p{L}0-9]+", "-"); }
}
