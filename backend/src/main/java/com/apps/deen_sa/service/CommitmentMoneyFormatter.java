package com.apps.deen_sa.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Shared display formatting for the monthly commitment projection. */
public final class CommitmentMoneyFormatter {
    private CommitmentMoneyFormatter() { }
    static String money(BigDecimal amount, String currency) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        formatter.setCurrency(Currency.getInstance(currency));
        return formatter.format(amount);
    }
}
