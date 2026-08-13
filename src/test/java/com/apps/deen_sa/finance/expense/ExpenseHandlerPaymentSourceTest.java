package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseHandlerPaymentSourceTest {

    @Test
    void recoversNamedCreditCardWhenModelReturnsOnlyGenericType() {
        ExpenseDto expense = new ExpenseDto();
        expense.setSourceAccount("CREDIT_CARD");
        expense.setRawText("Weekend dinner for ₹3,400 paid via HDFC Credit Card.");

        assertThat(ExpenseHandler.specificSourceAccount(expense)).isEqualTo("HDFC Credit Card");
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPI", "my upi", "Bank", "Bank / UPI", "BANK/UPI", "bank account", "BANK_ACCOUNT",
            "HDFC bank account", "my HDFC salary bank account"})
    void normalizesModelAndButtonBankLabels(String source) {
        assertThat(ExpenseHandler.normalizeSourceType(source)).isEqualTo("BANK_ACCOUNT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Card", "Credit", "Credit Card", "CREDIT_CARD", "HDFC Credit Card",
            "ICICI Amazon Pay credit card"})
    void normalizesCreditCardLabels(String source) {
        assertThat(ExpenseHandler.normalizeSourceType(source)).isEqualTo("CREDIT_CARD");
    }
}
