package com.apps.deen_sa.conversation.interpretation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventFieldsTest {
    @Test
    void removesModelPlaceholdersAndImplausibleDatesFromGenericFacts() {
        EventFields fields = EventFields.from(Map.of(
                        "quantity", 0,
                        "description", "Internet",
                        "unknownValue", "N/A",
                        "observedDate", "1970-01-01",
                        "rawText", "Recorded internet service"))
                .sanitized(List.of(new FieldEvidence("description", "Internet", "internet", .99)));

        assertThat(fields.asMap()).containsEntry("quantity", 0).containsEntry("description", "Internet");
        assertThat(fields.asMap()).doesNotContainKeys("unknownValue", "observedDate");
    }

    @Test
    void retainsArbitraryExtensionFactsWithoutAUnionDto() {
        EventFields fields = EventFields.from(java.util.Map.of(
                        "quantity", 20,
                        "unit", "kg",
                        "sku", "rice"
                ))
                .sanitized(List.of(new FieldEvidence("sku", "rice", "rice", .99)));

        assertThat(fields.asMap()).containsEntry("quantity", 20).containsEntry("unit", "kg").containsEntry("sku", "rice");
    }
}
