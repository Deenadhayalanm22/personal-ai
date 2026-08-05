package com.apps.deen_sa.conversation.interpretation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Closed financial schema used by Structured Outputs; unknown model fields cannot reach execution. */
public record EventFields(
        BigDecimal amount,
        String category,
        String subcategory,
        String merchantName,
        String sourceAccount,
        BigDecimal sourceBalance,
        BigDecimal creditLimit,
        Integer creditCardDueDay,
        LocalDate transactionDate,
        List<String> tags,
        String rawText
) {
    public Map<String, Object> asMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "amount", amount); put(values, "category", category);
        put(values, "subcategory", subcategory); put(values, "merchantName", merchantName);
        put(values, "sourceAccount", sourceAccount); put(values, "sourceBalance", sourceBalance);
        put(values, "creditLimit", creditLimit);
        put(values, "creditCardDueDay", creditCardDueDay);
        put(values, "transactionDate", transactionDate); put(values, "tags", tags); put(values, "rawText", rawText);
        return values;
    }

    public static EventFields from(Map<String, Object> values) {
        return new EventFields(decimal(values.get("amount")), string(values.get("category")),
                string(values.get("subcategory")), string(values.get("merchantName")),
                string(values.get("sourceAccount")), decimal(values.get("sourceBalance")), decimal(values.get("creditLimit")),
                integer(values.get("creditCardDueDay")),
                values.get("transactionDate") == null ? null : LocalDate.parse(values.get("transactionDate").toString()),
                null, string(values.get("rawText")));
    }

    public EventFields sanitized(List<FieldEvidence> evidence) {
        Set<String> supported = evidence == null ? Set.of() : evidence.stream()
                .filter(item -> item != null && item.field() != null && item.evidence() != null && !item.evidence().isBlank())
                .map(FieldEvidence::field).collect(java.util.stream.Collectors.toSet());
        return new EventFields(
                positiveOrNull(amount),
                textOrNull(category), textOrNull(subcategory), textOrNull(merchantName),
                supported.contains("sourceAccount") ? textOrNull(sourceAccount) : null,
                supported.contains("sourceBalance") ? nonNegativeOrNull(sourceBalance) : null,
                supported.contains("creditLimit") ? positiveOrNull(creditLimit) : null,
                supported.contains("creditCardDueDay") ? validDueDayOrNull(creditCardDueDay) : null,
                supported.contains("transactionDate") ? plausibleDateOrNull(transactionDate) : null,
                tags == null || tags.isEmpty() ? null : tags.stream().filter(tag -> tag != null && !tag.isBlank()).toList(),
                textOrNull(rawText));
    }

    private static void put(Map<String, Object> values, String key, Object value) { if (value != null) values.put(key, value); }
    private static String string(Object value) { return value == null ? null : value.toString(); }
    private static BigDecimal decimal(Object value) { return value == null ? null : new BigDecimal(value.toString()); }
    private static Integer integer(Object value) { return value == null ? null : Integer.valueOf(value.toString()); }
    private static String textOrNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isBlank() || normalized.equals("/")
                || java.util.Set.of("null", "none", "n/a", "na", "unknown", "not provided")
                .contains(normalized.toLowerCase())) return null;
        return normalized;
    }
    private static BigDecimal positiveOrNull(BigDecimal value) { return value == null || value.signum() <= 0 ? null : value; }
    private static BigDecimal nonNegativeOrNull(BigDecimal value) { return value == null || value.signum() < 0 ? null : value; }
    private static LocalDate plausibleDateOrNull(LocalDate value) {
        if (value == null || value.getYear() < 2000 || value.isAfter(LocalDate.now().plusDays(1))) return null;
        return value;
    }
    private static Integer validDueDayOrNull(Integer value) { return value != null && value >= 1 && value <= 31 ? value : null; }
}
