package com.apps.deen_sa.grocery;

import com.apps.deen_sa.core.ledger.CoreEventEntity;
import com.apps.deen_sa.core.ledger.GenericLedgerService;
import com.apps.deen_sa.extension.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GroceryStockCapability implements EventCapability {
    private static final Pattern INPUT = Pattern.compile("(?i).*?([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(kg|g|piece|pieces|pcs)\\s+([\\p{L}0-9][\\p{L}0-9 '-]*)");
    private final String type;
    private final GenericLedgerService ledger;

    GroceryStockCapability(String type, GenericLedgerService ledger) { this.type = type; this.ledger = ledger; }
    @Override public String eventType() { return type; }
    @Override public String schemaVersion() { return "1.0.0"; }
    @Override public Set<String> fields() { return Set.of("sku", "quantity", "unit", "rawText"); }
    @Override public Map<String, String> fieldTypes() {
        return Map.of("sku", "string", "quantity", "number", "unit", "string", "rawText", "string");
    }
    @Override public String extractionInstructions() {
        return "Extract only the explicitly stated product, quantity and stock unit.";
    }

    @Override public CapabilityResult handle(ExtensionEvent event, String rawText, CapabilityContext context, boolean continuation) {
        Matcher matcher = INPUT.matcher(rawText == null ? "" : rawText);
        if (!matcher.matches()) return CapabilityResult.followup(
                "Which product, quantity, and unit should I record?", List.of("sku", "quantity", "unit"), Map.of("rawText", rawText == null ? "" : rawText));
        BigDecimal quantity = new BigDecimal(matcher.group(1).replace(",", ""));
        String unit = normalizeUnit(matcher.group(2));
        String sku = matcher.group(3).trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}0-9]+", "-");
        boolean receipt = type.equals("GROCERY_STOCK_RECEIVED");
        List<MovementPlan> movements = List.of(
                new MovementPlan("sku:" + sku, receipt ? "supplier" : "store-stock", quantity.negate(), unit),
                new MovementPlan("sku:" + sku, receipt ? "store-stock" : "customer", quantity, unit));
        Long tenantId = metadataLong(context, "tenantId", context.getUserId());
        Object inboundMessageId = context.getMetadata() == null ? null : context.getMetadata().get("inboundMessageId");
        String idempotency = Objects.toString(inboundMessageId,
                "conversation:" + context.getSessionId() + ":" + Integer.toUnsignedString(Objects.hash(type, rawText)));
        ExecutionPlan plan = new ExecutionPlan(tenantId, "grocery", type, schemaVersion(), Instant.now(),
                String.valueOf(context.getUserId()), Map.of("sku", sku, "quantity", quantity, "unit", unit, "rawText", rawText),
                Map.of("rawText", rawText), "grocery-stock-v1", idempotency, null, movements, List.of());
        CoreEventEntity saved = ledger.commit(plan);
        return CapabilityResult.saved((receipt ? "Received " : "Sold ") + quantity.stripTrailingZeros().toPlainString()
                + " " + unit + " of " + matcher.group(3).trim() + ".", saved);
    }

    private static Long metadataLong(CapabilityContext context, String key, Long fallback) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }
    private static String normalizeUnit(String value) { return value.toLowerCase(Locale.ROOT).startsWith("piece") || value.equalsIgnoreCase("pcs") ? "piece" : value.toLowerCase(Locale.ROOT); }
}
