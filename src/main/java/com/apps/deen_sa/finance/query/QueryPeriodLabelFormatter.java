package com.apps.deen_sa.finance.query;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Produces user-facing labels for named and rolling query periods. */
final class QueryPeriodLabelFormatter {
    private static final Pattern ROLLING = Pattern.compile(
            "^LAST_(?:(\\d+)_)?(DAY|DAYS|WEEK|WEEKS|MONTH|MONTHS|YEAR|YEARS)$");

    private QueryPeriodLabelFormatter() { }

    static String describe(String period) {
        if (period == null || period.isBlank()) return "for the requested period";
        String normalized = period.trim().toUpperCase(Locale.ROOT);
        String named = switch (normalized) {
            case "TODAY" -> "today";
            case "THIS_WEEK" -> "this week";
            case "THIS_MONTH" -> "this month";
            case "THIS_YEAR" -> "this year";
            default -> null;
        };
        if (named != null) return named;

        Matcher rolling = ROLLING.matcher(normalized);
        if (!rolling.matches()) return "for the requested period";
        int amount = rolling.group(1) == null ? 1 : Integer.parseInt(rolling.group(1));
        String unit = rolling.group(2).toLowerCase(Locale.ROOT);
        if (amount == 1 && unit.endsWith("s")) unit = unit.substring(0, unit.length() - 1);
        if (amount != 1 && !unit.endsWith("s")) unit += "s";
        return "in the last " + amount + " " + unit;
    }
}
