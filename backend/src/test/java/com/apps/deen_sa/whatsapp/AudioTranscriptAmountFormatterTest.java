package com.apps.deen_sa.whatsapp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudioTranscriptAmountFormatterTest {
    @Test
    void removesInferredCurrencyFromNumericExpenseAmount() {
        assertThat(AudioTranscriptAmountFormatter.format(
                "Paid $25 on chocolate from KK shop, paid from UPI."))
                .isEqualTo("Paid 25 on chocolate from KK shop, paid from UPI.");
        assertThat(AudioTranscriptAmountFormatter.format("Spent 250 rupees at Swiggy"))
                .isEqualTo("Spent 250 at Swiggy");
    }

    @Test
    void writesCommonSpokenAmountsAsDigits() {
        assertThat(AudioTranscriptAmountFormatter.format("Paid twenty five dollars at KK shop"))
                .isEqualTo("Paid 25 at KK shop");
        assertThat(AudioTranscriptAmountFormatter.format("Spent one hundred and twenty five on groceries"))
                .isEqualTo("Spent 125 on groceries");
    }
}
