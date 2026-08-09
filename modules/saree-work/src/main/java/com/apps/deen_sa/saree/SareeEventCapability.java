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
    static final String MATERIAL_ISSUED = "SAREE_MATERIAL_ISSUED";
    static final String PRODUCTION_SURRENDERED = "SAREE_PRODUCTION_SURRENDERED";
    static final String WAGE_STATEMENT_APPROVED = "SAREE_WAGE_STATEMENT_APPROVED";
    static final String WAGE_PAID = "SAREE_WAGE_PAID";

    private static final BigDecimal WAGE_RATE = new BigDecimal("100");
    private static final String RULE_VERSION = "saree-wage-100-v1";

    private final String type;
    private final GenericLedgerService ledger;
    private final SareeWorkflowRepository workflows;

    SareeEventCapability(String type, GenericLedgerService ledger, SareeWorkflowRepository workflows) {
        this.type = type;
        this.ledger = ledger;
        this.workflows = workflows;
    }

    @Override public String eventType() { return type; }
    @Override public String schemaVersion() { return "1.0.0"; }
    @Override public Set<String> fields() {
        return Set.of("employee", "quantity", "unit", "batchId", "paymentMethod", "rawText");
    }
    @Override public Map<String, String> fieldTypes() {
        return Map.of("employee", "string", "quantity", "number", "unit", "string", "batchId", "string",
                "paymentMethod", "string", "rawText", "string");
    }
    @Override public String extractionInstructions() {
        return "Saree job-work event. Extract only explicit facts; the extension resolves the single open batch and wage context.";
    }

    @Override
    public CapabilityResult handle(ExtensionEvent event, String rawText, CapabilityContext context, boolean continuation) {
        return switch (type) {
            case MATERIAL_ISSUED -> issue(event, rawText, context);
            case PRODUCTION_SURRENDERED -> surrender(event, rawText, context);
            case WAGE_STATEMENT_APPROVED -> approve(rawText, context);
            case WAGE_PAID -> pay(event, rawText, context);
            default -> CapabilityResult.unknown("Unsupported saree event " + type + ".");
        };
    }

    private CapabilityResult issue(ExtensionEvent event, String rawText, CapabilityContext context) {
        String employee = firstNonBlank(stringField(event, "employee"), employee(rawText));
        BigDecimal quantity = firstNonNull(number(rawText), decimalField(event, "quantity"));
        if (employee == null) return followup("Which employee should receive the thread?", "employee", rawText);
        if (quantity == null) return followup("How much thread should I assign?", "quantity", rawText);

        Long tenantId = tenantId(context);
        String batchId = nextBatchId(tenantId);
        Map<String, Object> facts = facts(rawText, employee, quantity, "m", batchId);
        facts.put("batchStatus", "OPEN");
        CoreEventEntity saved = commit(context, facts, null, List.of(
                movement("thread-assignment", "assignment-source", quantity.negate(), "m"),
                movement("thread-assignment", batch(batchId, "assigned"), quantity, "m")));

        return CapabilityResult.saved("Done. Assigned " + format(quantity) + " m of thread to " + employee
                + " today in batch " + batchId + ". The batch is open.", saved);
    }

    private CapabilityResult surrender(ExtensionEvent event, String rawText, CapabilityContext context) {
        String employee = firstNonBlank(stringField(event, "employee"), employee(rawText));
        BigDecimal quantity = firstNonNull(number(rawText), decimalField(event, "quantity"));
        if (employee == null) return followup("Which employee surrendered the sarees?", "employee", rawText);
        if (quantity == null) return followup("How many sarees were surrendered?", "quantity", rawText);

        Long tenantId = tenantId(context);
        List<CoreEventEntity> batches = openBatches(tenantId, employee);
        if (batches.isEmpty()) return CapabilityResult.info("I couldn't find an open batch for " + employee + ".");
        if (batches.size() > 1) return followup("Which open batch should I record this against?", "batchId", rawText);

        CoreEventEntity assignment = batches.getFirst();
        String batchId = textFact(assignment, "batchId");
        BigDecimal earned = quantity.multiply(WAGE_RATE);
        Map<String, Object> facts = facts(rawText, employee, quantity, "piece", batchId);
        facts.put("acceptedQuantity", quantity);
        facts.put("wageRate", WAGE_RATE);
        facts.put("earnedWage", earned);
        facts.put("acceptanceMode", "AUTOMATIC_MVP");
        CoreEventEntity saved = commit(context, facts, assignment.getIdempotencyKey(), List.of(
                movement("saree", batch(batchId, "reported"), quantity.negate(), "piece"),
                movement("saree", batch(batchId, "accepted"), quantity, "piece"),
                movement("inr-payable", "wage-accrual", earned.negate(), "INR"),
                movement("inr-payable", employee(employee, "earned"), earned, "INR")));

        return CapabilityResult.saved("Recorded " + format(quantity) + " sarees for " + batchId + ". " + employee
                + " earned ₹" + format(earned) + " at ₹" + format(WAGE_RATE) + " each. Approve this wage?", saved);
    }

    private CapabilityResult approve(String rawText, CapabilityContext context) {
        Long tenantId = tenantId(context);
        CoreEventEntity surrender = latestUnapprovedSurrender(tenantId).orElse(null);
        if (surrender == null) return CapabilityResult.info("There is no wage statement waiting for approval.");

        String employee = textFact(surrender, "employee");
        String batchId = textFact(surrender, "batchId");
        BigDecimal amount = decimalFact(surrender, "earnedWage");
        Map<String, Object> facts = facts(rawText, employee, amount, "INR", batchId);
        facts.put("approvedWage", amount);
        facts.put("statementStatus", "APPROVED");
        CoreEventEntity saved = commit(context, facts, surrender.getIdempotencyKey(), List.of(
                movement("inr-payable", "wage-approval", amount.negate(), "INR"),
                movement("inr-payable", employee(employee, "approved"), amount, "INR")));

        return CapabilityResult.saved("Approved ₹" + format(amount) + " for " + employee
                + ". Has it been paid by cash or bank?", saved);
    }

    private CapabilityResult pay(ExtensionEvent event, String rawText, CapabilityContext context) {
        BigDecimal amount = firstNonNull(number(rawText), decimalField(event, "quantity"));
        String method = firstNonBlank(paymentMethod(rawText), normalizedPaymentMethod(stringField(event, "paymentMethod")));
        if (amount == null) return followup("How much was paid?", "quantity", rawText);
        if (method == null) return followup("Was it paid by cash or bank?", "paymentMethod", rawText);

        Long tenantId = tenantId(context);
        CoreEventEntity approval = latestUnpaidApproval(tenantId).orElse(null);
        if (approval == null) return CapabilityResult.info("There is no approved wage waiting for payment.");
        String employee = textFact(approval, "employee");
        String batchId = textFact(approval, "batchId");
        BigDecimal approved = decimalFact(approval, "approvedWage");
        if (amount.compareTo(approved) > 0) {
            return CapabilityResult.info("The approved balance is ₹" + format(approved)
                    + ". Please confirm before recording a larger payment.");
        }

        Map<String, Object> facts = facts(rawText, employee, amount, "INR", batchId);
        facts.put("paymentMethod", method);
        facts.put("paidWage", amount);
        facts.put("outstandingWage", approved.subtract(amount));
        String cashContainer = "CASH".equals(method) ? "business-cash" : "business-bank";
        CoreEventEntity saved = commit(context, facts, approval.getIdempotencyKey(), List.of(
                movement("inr", cashContainer, amount.negate(), "INR"),
                movement("inr", employee(employee, "received"), amount, "INR"),
                movement("inr-payable", employee(employee, "approved"), amount.negate(), "INR"),
                movement("inr-payable", "wage-settlement", amount, "INR")));

        BigDecimal balance = approved.subtract(amount);
        return CapabilityResult.saved("Payment recorded: ₹" + format(amount) + " " + method.toLowerCase(Locale.ROOT)
                + " to " + employee + " today. Earned ₹" + format(approved) + ", paid ₹" + format(amount)
                + ", balance ₹" + format(balance) + ". Batch " + batchId + " is still open.", saved);
    }

    private Optional<CoreEventEntity> latestUnapprovedSurrender(Long tenantId) {
        List<CoreEventEntity> approvals = workflows
                .findByTenantIdAndExtensionIdAndEventTypeOrderByIdDesc(tenantId, "saree-job-work", WAGE_STATEMENT_APPROVED);
        Set<String> approvedCauses = approvals.stream().map(CoreEventEntity::getCausationId).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return workflows.findByTenantIdAndExtensionIdAndEventTypeOrderByIdDesc(
                        tenantId, "saree-job-work", PRODUCTION_SURRENDERED).stream()
                .filter(value -> !approvedCauses.contains(value.getIdempotencyKey())).findFirst();
    }

    private Optional<CoreEventEntity> latestUnpaidApproval(Long tenantId) {
        List<CoreEventEntity> payments = workflows
                .findByTenantIdAndExtensionIdAndEventTypeOrderByIdDesc(tenantId, "saree-job-work", WAGE_PAID);
        Set<String> paidCauses = payments.stream().map(CoreEventEntity::getCausationId).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return workflows.findByTenantIdAndExtensionIdAndEventTypeOrderByIdDesc(
                        tenantId, "saree-job-work", WAGE_STATEMENT_APPROVED).stream()
                .filter(value -> !paidCauses.contains(value.getIdempotencyKey())).findFirst();
    }

    private List<CoreEventEntity> openBatches(Long tenantId, String employee) {
        return workflows.findByTenantIdAndExtensionIdAndEventTypeOrderByIdDesc(
                        tenantId, "saree-job-work", MATERIAL_ISSUED).stream()
                .filter(value -> employee.equalsIgnoreCase(textFact(value, "employee")))
                .filter(value -> "OPEN".equals(textFact(value, "batchStatus"))).toList();
    }

    private String nextBatchId(Long tenantId) {
        long count = workflows.countByTenantIdAndExtensionIdAndEventType(tenantId, "saree-job-work", MATERIAL_ISSUED);
        return "SW-" + (101 + count);
    }

    private CoreEventEntity commit(CapabilityContext context, Map<String, Object> facts, String causationId,
                                   List<MovementPlan> movements) {
        String messageId = messageId(context, String.valueOf(facts.get("rawText")));
        return ledger.commit(new ExecutionPlan(tenantId(context), "saree-job-work", type, schemaVersion(), Instant.now(),
                String.valueOf(context.getUserId()), facts, Map.of("rawText", facts.get("rawText")), RULE_VERSION,
                messageId, causationId, movements, List.of()));
    }

    private Map<String, Object> facts(String rawText, String employee, BigDecimal quantity, String unit, String batchId) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("rawText", rawText);
        facts.put("employee", employee);
        facts.put("batchId", batchId);
        facts.put("quantity", quantity);
        facts.put("unit", unit);
        return facts;
    }

    private CapabilityResult followup(String question, String field, String rawText) {
        return CapabilityResult.followup(question, List.of(field), Map.of("rawText", rawText));
    }

    private MovementPlan movement(String resource, String container, BigDecimal quantity, String unit) {
        return new MovementPlan(resource, container, quantity, unit);
    }

    private String employee(String text) {
        if (text == null) return null;
        List<Pattern> patterns = List.of(
                Pattern.compile("(?i)^(?:give|gave|issue|issued|assign|assigned)\\s+([\\p{L}][\\p{L} .'-]*?)\\s+[₹0-9]"),
                Pattern.compile("(?i)^([\\p{L}][\\p{L} .'-]*?)\\s+(?:surrenders?|surrendered|submits?|submitted|produces?|produced|brought\\s+back)"),
                Pattern.compile("(?i)(?:to|from|by)\\s+([\\p{L}][\\p{L} .'-]*?)(?:\\s+today)?[.!]?$"));
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text.trim());
            if (matcher.find()) return matcher.group(1).trim();
        }
        return null;
    }

    private BigDecimal number(String text) {
        Matcher matcher = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)").matcher(text == null ? "" : text);
        return matcher.find() ? new BigDecimal(matcher.group(1).replace(",", "")) : null;
    }

    private String paymentMethod(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (normalized.contains("cash")) return "CASH";
        if (normalized.contains("bank") || normalized.contains("upi")) return "BANK";
        return null;
    }

    private String normalizedPaymentMethod(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("CASH")) return "CASH";
        if (normalized.contains("BANK") || normalized.contains("UPI")) return "BANK";
        return null;
    }

    private String stringField(ExtensionEvent event, String field) {
        if (event == null || event.facts() == null || event.facts().get(field) == null) return null;
        String value = String.valueOf(event.facts().get(field)).trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private BigDecimal decimalField(ExtensionEvent event, String field) {
        String value = stringField(event, field);
        if (value == null) return null;
        try { return new BigDecimal(value.replace(",", "")); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String firstNonBlank(String first, String second) { return first == null || first.isBlank() ? second : first; }
    private <T> T firstNonNull(T first, T second) { return first == null ? second : first; }

    private String format(BigDecimal value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    private String batch(String batchId, String state) { return "batch:" + batchId + ":" + state; }
    private String employee(String employee, String state) { return "employee:" + slug(employee) + ":" + state; }
    private String slug(String value) { return value.toLowerCase(Locale.ROOT).trim().replaceAll("[^\\p{L}0-9]+", "-"); }
    private String textFact(CoreEventEntity event, String name) { return String.valueOf(event.getFacts().get(name)); }
    private BigDecimal decimalFact(CoreEventEntity event, String name) {
        return new BigDecimal(String.valueOf(event.getFacts().get(name)));
    }
    private Long tenantId(CapabilityContext context) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get("tenantId");
        return value instanceof Number number ? number.longValue() : context.getUserId();
    }
    private String messageId(CapabilityContext context, String text) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get("inboundMessageId");
        return value == null ? "conversation:" + context.getSessionId() + ":"
                + Integer.toUnsignedString(Objects.hash(type, text)) : value.toString();
    }
}
