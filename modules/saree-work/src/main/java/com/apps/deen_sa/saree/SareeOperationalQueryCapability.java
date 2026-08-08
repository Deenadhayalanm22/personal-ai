package com.apps.deen_sa.saree;

import com.apps.deen_sa.extension.api.*;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SareeOperationalQueryCapability implements EventCapability {
    private final SareeLedgerQueryRepository repository;
    SareeOperationalQueryCapability(SareeLedgerQueryRepository repository) { this.repository = repository; }
    @Override public String eventType() { return "SAREE_OPERATION_QUERY"; }
    @Override public String schemaVersion() { return "1.0.0"; }
    @Override public Set<String> fields() { return Set.of("employee", "metric", "rawText"); }
    @Override public String extractionInstructions() { return "Read-only saree custody, finished-stock and wage-payable query."; }

    @Override public CapabilityResult handle(ExtensionEvent event, String rawText, CapabilityContext context, boolean continuation) {
        Long tenantId = tenantId(context);
        String normalized = rawText.toLowerCase(Locale.ROOT);
        if (normalized.contains("finished") || normalized.contains("stock")) {
            BigDecimal value = repository.balance(tenantId, "saree", "finished-goods", "piece");
            return CapabilityResult.info("Finished saree stock is " + number(value) + " pieces.");
        }
        String employee = employee(rawText);
        if (employee == null) return CapabilityResult.followup("Which employee should I check?", List.of("employee"), rawText);
        if (normalized.contains("payable") || normalized.contains("wage") || normalized.contains("owe")) {
            BigDecimal value = repository.balance(tenantId, "inr-payable", "employee:" + slug(employee) + ":payable", "INR");
            return CapabilityResult.info("Wages payable to " + employee + " are ₹" + number(value) + ".");
        }
        BigDecimal value = repository.balance(tenantId, "thread", "employee:" + slug(employee) + ":custody", "m");
        return CapabilityResult.info(employee + " has " + number(value) + " metres of thread in custody.");
    }

    private Long tenantId(CapabilityContext context) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get("tenantId");
        return value instanceof Number number ? number.longValue() : context.getUserId();
    }
    private String employee(String text) {
        Matcher matcher = Pattern.compile("(?i)(?:for|of|to|does)\\s+([\\p{L}][\\p{L} .'-]*?)(?:\\s+(?:have|has|owe|payable)|\\?|$)").matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
    private String slug(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}0-9]+", "-"); }
    private String number(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
}
