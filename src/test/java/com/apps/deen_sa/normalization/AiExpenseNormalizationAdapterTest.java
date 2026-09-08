package com.apps.deen_sa.normalization;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiExpenseNormalizationAdapterTest {

    @Test
    void doesNotGuessBetweenMultipleCardsForAGenericReference() {
        assertThat(AiExpenseNormalizationAdapter.resolveGenericCreditCardReference(
                "Paid 300 for dinner using credit card",
                List.of("HDFC credit card", "ICICI credit card")))
                .isNull();
    }

    @Test
    void resolvesAGenericReferenceWhenThereIsOnlyOneCard() {
        assertThat(AiExpenseNormalizationAdapter.resolveGenericCreditCardReference(
                "Paid 500 for groceries using credit card",
                List.of("HDFC credit card")))
                .isEqualTo("HDFC credit card");
    }

    @Test
    void doesNotTreatANamedCardAsAGenericReference() {
        assertThat(AiExpenseNormalizationAdapter.hasGenericCreditCardReference(
                "Paid 700 for fuel using ICICI credit card"))
                .isFalse();
    }
}
