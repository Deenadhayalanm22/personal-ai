package com.apps.deen_sa.conversation.interpretation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventFieldsTest {
    @Test
    void removesModelPlaceholdersForUnknownFinancialFields() {
        EventFields fields = new EventFields(BigDecimal.ZERO, "null", "Internet", "none", "N/A", null,
                BigDecimal.ZERO, null, null, LocalDate.of(1970, 1, 1), List.of(), "Paid internet bill")
                .sanitized(List.of(new FieldEvidence("subcategory", "Internet", "internet bill", .99)));

        assertThat(fields.amount()).isNull();
        assertThat(fields.sourceBalance()).isNull();
        assertThat(fields.transactionDate()).isNull();
        assertThat(fields.category()).isNull();
        assertThat(fields.sourceAccount()).isNull();
        assertThat(fields.subcategory()).isEqualTo("Internet");
    }

    @Test
    void keepsGroundedIncomeDestinationSeparateFromExpenseSource() {
        EventFields fields = EventFields.from(java.util.Map.of(
                        "amount", 80000,
                        "destinationAccount", "HDFC salary account",
                        "rawText", "Salary credited to HDFC salary account"
                ))
                .sanitized(List.of(new FieldEvidence(
                        "destinationAccount", "HDFC salary account", "HDFC salary account", .99)));

        assertThat(fields.amount()).isEqualByComparingTo("80000");
        assertThat(fields.destinationAccount()).isEqualTo("HDFC salary account");
        assertThat(fields.sourceAccount()).isNull();
    }
}
