package com.apps.deen_sa.finance.query;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recovers flexible English rolling periods from the original query text. */
public final class RollingQueryPeriodResolver {
    private static final Pattern ROLLING = Pattern.compile(
            "(?i)\\blast\\s+(\\d+)\\s+(day|days|week|weeks|month|months|year|years)\\b");

    private RollingQueryPeriodResolver() { }

    public static String resolve(String classifiedPeriod, String rawText) {
        if (rawText == null) return classifiedPeriod;
        Matcher matcher = ROLLING.matcher(rawText);
        if (!matcher.find()) return classifiedPeriod;
        int amount = Integer.parseInt(matcher.group(1));
        if (amount < 1) return classifiedPeriod;
        String unit = matcher.group(2).toUpperCase(Locale.ROOT);
        if (amount == 1 && unit.endsWith("S")) unit = unit.substring(0, unit.length() - 1);
        if (amount != 1 && !unit.endsWith("S")) unit += "S";
        return "LAST_" + amount + "_" + unit;
    }
}
