package com.apps.deen_sa.saree;

import com.apps.deen_sa.core.ledger.CoreEventEntity;
import com.apps.deen_sa.core.ledger.GenericLedgerService;
import com.apps.deen_sa.extension.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SareeEventCapability implements EventCapability {
    private static final BigDecimal DEFAULT_WAGE_RATE = new BigDecimal("100");
    private final String type;
    private final GenericLedgerService ledger;

    SareeEventCapability(String type, GenericLedgerService ledger) { this.type = type; this.ledger = ledger; }
    @Override public String eventType() { return type; }
    @Override public String schemaVersion() { return "1.0.0"; }
    @Override public Set<String> fields() { return Set.of("employee", "quantity", "unit", "rate", "rawText"); }
    @Override public Map<String, String> fieldTypes() {
        return Map.of("employee", "string", "quantity", "number", "unit", "string", "rate", "number", "rawText", "string");
    }
    @Override public String extractionInstructions() {
        return "Extract the named employee, explicitly stated quantity and unit. Never infer material consumption.";
    }

    @Override
    public CapabilityResult handle(ExtensionEvent event, String rawText, CapabilityContext context, boolean continuation) {
        Parsed parsed = parse(rawText);
        List<String> missing = new ArrayList<>();
        if (parsed.employee() == null) missing.add("employee");
        if (!type.equals("SAREE_EMPLOYEE_REGISTERED") && parsed.quantity() == null) missing.add("quantity");
        if (!missing.isEmpty()) return CapabilityResult.followup(question(missing.getFirst()), missing, Map.of("rawText", rawText));

        Long tenantId = tenantId(context);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("employee", parsed.employee()); facts.put("rawText", rawText);
        if (parsed.quantity() != null) { facts.put("quantity", parsed.quantity()); facts.put("unit", unit()); }
        List<MovementPlan> movements = movements(parsed);
        List<ObservationPlan> observations = observations(parsed);
        if (type.equals("SAREE_PRODUCTION_ACCEPTED")) facts.put("wageRate", DEFAULT_WAGE_RATE);
        String messageId = messageId(context, rawText);
        ExecutionPlan plan = new ExecutionPlan(tenantId, "saree-job-work", type, schemaVersion(), Instant.now(),
                String.valueOf(context.getUserId()), facts, Map.of("rawText", rawText), "saree-rules-v1", messageId,
                null, movements, observations);
        CoreEventEntity saved = ledger.commit(plan);
        return CapabilityResult.saved(confirmation(parsed), saved);
    }

    private List<MovementPlan> movements(Parsed value) {
        if (type.equals("SAREE_MATERIAL_ISSUED")) return List.of(
                new MovementPlan("thread", "raw-material-stock", value.quantity().negate(), "m"),
                new MovementPlan("thread", "employee:" + slug(value.employee()) + ":custody", value.quantity(), "m"));
        if (type.equals("SAREE_PRODUCTION_SURRENDERED")) return List.of(
                new MovementPlan("saree", "employee:" + slug(value.employee()) + ":produced", value.quantity().negate(), "piece"),
                new MovementPlan("saree", "inspection-queue", value.quantity(), "piece"));
        if (type.equals("SAREE_PRODUCTION_ACCEPTED")) return List.of(
                new MovementPlan("saree", "inspection-queue", value.quantity().negate(), "piece"),
                new MovementPlan("saree", "finished-goods", value.quantity(), "piece"),
                new MovementPlan("inr-payable", "wage-accrual", value.quantity().multiply(DEFAULT_WAGE_RATE).negate(), "INR"),
                new MovementPlan("inr-payable", "employee:" + slug(value.employee()) + ":payable",
                        value.quantity().multiply(DEFAULT_WAGE_RATE), "INR"));
        if (type.equals("SAREE_WAGE_PAID")) return List.of(
                new MovementPlan("inr", "business-cash", value.quantity().negate(), "INR"),
                new MovementPlan("inr", "employee:" + slug(value.employee()) + ":received", value.quantity(), "INR"),
                new MovementPlan("inr-payable", "employee:" + slug(value.employee()) + ":payable", value.quantity().negate(), "INR"),
                new MovementPlan("inr-payable", "wage-settlement", value.quantity(), "INR"));
        return List.of();
    }

    private List<ObservationPlan> observations(Parsed value) {
        if (type.equals("SAREE_EMPLOYEE_REGISTERED")) return List.of(
                new ObservationPlan("employee", slug(value.employee()), BigDecimal.ONE, "registered", Instant.now()));
        return List.of();
    }

    private String unit() { return type.equals("SAREE_MATERIAL_ISSUED") ? "m" : type.equals("SAREE_WAGE_PAID") ? "INR" : "piece"; }
    private String question(String field) { return field.equals("employee") ? "Which employee is this for?" : "What quantity should I record?"; }
    private String confirmation(Parsed value) { return switch (type) {
        case "SAREE_EMPLOYEE_REGISTERED" -> "Registered employee " + value.employee() + ".";
        case "SAREE_MATERIAL_ISSUED" -> "Issued " + value.quantity().stripTrailingZeros().toPlainString() + " metres of thread to " + value.employee() + ".";
        case "SAREE_PRODUCTION_SURRENDERED" -> "Recorded " + value.quantity().stripTrailingZeros().toPlainString() + " sarees surrendered by " + value.employee() + ".";
        case "SAREE_PRODUCTION_ACCEPTED" -> "Accepted " + value.quantity().stripTrailingZeros().toPlainString() + " sarees from " + value.employee() + ".";
        default -> "Recorded ₹" + value.quantity().stripTrailingZeros().toPlainString() + " wage payment to " + value.employee() + ".";
    }; }

    private Parsed parse(String text) {
        BigDecimal quantity = number(text);
        String employee = employee(text);
        return new Parsed(employee, quantity);
    }

    private BigDecimal number(String text) {
        Matcher matcher = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)").matcher(text == null ? "" : text);
        return matcher.find() ? new BigDecimal(matcher.group(1).replace(",", "")) : null;
    }

    private String employee(String text) {
        if (text == null) return null;
        Matcher explicit = Pattern.compile("(?i)(?:employee|worker)\\s+([\\p{L}][\\p{L} .'-]*?)(?:\\s+(?:with|for|to|gave|issued|surrendered|submitted|produced|accepted|wage|salary)|$)").matcher(text);
        if (explicit.find()) return explicit.group(1).trim();
        Matcher relation = Pattern.compile("(?i)(?:to|from|by)\\s+([\\p{L}][\\p{L} .'-]*)$").matcher(text);
        return relation.find() ? relation.group(1).trim() : null;
    }

    private Long tenantId(CapabilityContext context) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get("tenantId");
        return value instanceof Number number ? number.longValue() : context.getUserId();
    }
    private String messageId(CapabilityContext context, String text) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get("inboundMessageId");
        return value == null ? "conversation:" + context.getSessionId() + ":" + Integer.toUnsignedString(Objects.hash(type, text)) : value.toString();
    }
    private String slug(String value) { return value.toLowerCase(Locale.ROOT).trim().replaceAll("[^\\p{L}0-9]+", "-"); }
    private record Parsed(String employee, BigDecimal quantity) { }
}
