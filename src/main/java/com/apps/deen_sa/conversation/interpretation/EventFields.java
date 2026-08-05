package com.apps.deen_sa.conversation.interpretation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Closed financial schema used by Structured Outputs; unknown model fields cannot reach execution. */
public record EventFields(
        BigDecimal amount,
        String category,
        String subcategory,
        String merchantName,
        String sourceAccount,
        BigDecimal sourceBalance,
        LocalDate transactionDate,
        List<String> tags,
        String rawText
) {
    public Map<String, Object> asMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "amount", amount); put(values, "category", category);
        put(values, "subcategory", subcategory); put(values, "merchantName", merchantName);
        put(values, "sourceAccount", sourceAccount); put(values, "sourceBalance", sourceBalance);
        put(values, "transactionDate", transactionDate); put(values, "tags", tags); put(values, "rawText", rawText);
        return values;
    }

    public static EventFields from(Map<String, Object> values) {
        return new EventFields(decimal(values.get("amount")), string(values.get("category")),
                string(values.get("subcategory")), string(values.get("merchantName")),
                string(values.get("sourceAccount")), decimal(values.get("sourceBalance")),
                values.get("transactionDate") == null ? null : LocalDate.parse(values.get("transactionDate").toString()),
                null, string(values.get("rawText")));
    }

    private static void put(Map<String, Object> values, String key, Object value) { if (value != null) values.put(key, value); }
    private static String string(Object value) { return value == null ? null : value.toString(); }
    private static BigDecimal decimal(Object value) { return value == null ? null : new BigDecimal(value.toString()); }
}
