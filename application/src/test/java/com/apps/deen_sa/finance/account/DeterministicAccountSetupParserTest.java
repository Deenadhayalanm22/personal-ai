package com.apps.deen_sa.finance.account;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAccountSetupParserTest {
    private final DeterministicAccountSetupParser parser = new DeterministicAccountSetupParser();

    @ParameterizedTest
    @CsvSource({
            "HDFC Millennia,100000,0,5",
            "ICICI Amazon Pay,150000,0,12",
            "SBI SimplyCLICK,80000,0,20"
    })
    void parsesProtectedJourneyCreditCards(String label, String limit, String outstanding, int dueDay) {
        var dto = parser.parse("Create my " + label + " credit card with limit " + limit
                + ", outstanding " + outstanding + " and due day " + dueDay).orElseThrow();

        assertThat(dto.getContainerType()).isEqualTo("CREDIT_CARD");
        assertThat(dto.getName()).isEqualTo(label + " credit card");
        assertThat(dto.getCapacityLimit()).isEqualByComparingTo(limit);
        assertThat(dto.getCurrentValue()).isEqualByComparingTo(outstanding);
        assertThat(dto.getDetails()).containsEntry("dueDay", dueDay);
    }

    @org.junit.jupiter.api.Test
    void parsesProtectedJourneySalaryAccount() {
        var dto = parser.parse("Create my HDFC salary bank account with a current balance of 20000").orElseThrow();

        assertThat(dto.getContainerType()).isEqualTo("BANK_ACCOUNT");
        assertThat(dto.getName()).isEqualTo("HDFC salary bank account");
        assertThat(dto.getCurrentValue()).isEqualByComparingTo("20000");
    }
}
