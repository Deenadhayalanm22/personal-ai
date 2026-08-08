package com.apps.deen_sa.finance.expense;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseHandlerPaymentSourceTest {

    @ParameterizedTest
    @ValueSource(strings = {"UPI", "my upi", "Bank", "Bank / UPI", "BANK/UPI", "bank account", "BANK_ACCOUNT"})
    void normalizesModelAndButtonBankLabels(String source) {
        assertThat(ExpenseHandler.normalizeSourceType(source)).isEqualTo("BANK_ACCOUNT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Card", "Credit", "Credit Card", "CREDIT_CARD"})
    void normalizesCreditCardLabels(String source) {
        assertThat(ExpenseHandler.normalizeSourceType(source)).isEqualTo("CREDIT_CARD");
    }
}
