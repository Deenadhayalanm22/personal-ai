package com.apps.deen_sa.conversation.interpretation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventFieldsTest {
    @Test
    void removesModelPlaceholdersForUnknownFinancialFields() {
        EventFields fields = new EventFields(BigDecimal.ZERO, "null", "Internet", "none", "N/A",
                BigDecimal.ZERO, LocalDate.of(1970, 1, 1), List.of(), "Paid internet bill")
                .sanitized(List.of(new FieldEvidence("subcategory", "Internet", "internet bill", .99)));

        assertThat(fields.amount()).isNull();
        assertThat(fields.sourceBalance()).isNull();
        assertThat(fields.transactionDate()).isNull();
        assertThat(fields.category()).isNull();
        assertThat(fields.sourceAccount()).isNull();
        assertThat(fields.subcategory()).isEqualTo("Internet");
    }
}
