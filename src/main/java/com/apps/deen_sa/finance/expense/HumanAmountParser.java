package com.apps.deen_sa.finance.expense;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HumanAmountParser {

    private static final Pattern NUMBER = Pattern.compile(
            "(?i)([-+]?[0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(k|thousand|lakh|lac|crore|cr)?");

    private HumanAmountParser() {
    }

    public static Optional<BigDecimal> parse(String text) {
        if (text == null) return Optional.empty();
        Matcher matcher = NUMBER.matcher(text.trim());
        if (!matcher.find()) return Optional.empty();

        BigDecimal value = new BigDecimal(matcher.group(1).replace(",", ""));
        String suffix = matcher.group(2);
        if (suffix == null) return Optional.of(value);

        BigDecimal multiplier = switch (suffix.toLowerCase(Locale.ROOT)) {
            case "k", "thousand" -> BigDecimal.valueOf(1_000);
            case "lakh", "lac" -> BigDecimal.valueOf(100_000);
            case "crore", "cr" -> BigDecimal.valueOf(10_000_000);
            default -> BigDecimal.ONE;
        };
        return Optional.of(value.multiply(multiplier));
    }
}
