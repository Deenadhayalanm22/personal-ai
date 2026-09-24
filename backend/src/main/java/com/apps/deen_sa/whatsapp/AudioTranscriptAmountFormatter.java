package com.apps.deen_sa.whatsapp;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps reviewed spoken expense amounts numeric and free of inferred currency labels. */
final class AudioTranscriptAmountFormatter {
    private static final String NUMBER_WORD = "zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|and";
    private static final Pattern SPOKEN_AMOUNT = Pattern.compile(
            "(?i)\\b(paid|spent|cost)\\s+((?:(?:" + NUMBER_WORD + ")\\b[\\s-]*){1,10})");
    private static final Pattern CURRENCY_BEFORE = Pattern.compile(
            "(?i)(?:[$₹€£]|\\b(?:USD|INR|dollars?|rupees?)\\b)\\s*(?=\\d)");
    private static final Pattern CURRENCY_AFTER = Pattern.compile(
            "(?i)(?<=\\d)\\s*(?:[$₹€£]|\\b(?:USD|INR|dollars?|rupees?)\\b)");
    private static final Map<String, Integer> VALUES = Map.ofEntries(
            Map.entry("zero", 0), Map.entry("one", 1), Map.entry("two", 2),
            Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5),
            Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8),
            Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11),
            Map.entry("twelve", 12), Map.entry("thirteen", 13), Map.entry("fourteen", 14),
            Map.entry("fifteen", 15), Map.entry("sixteen", 16), Map.entry("seventeen", 17),
            Map.entry("eighteen", 18), Map.entry("nineteen", 19), Map.entry("twenty", 20),
            Map.entry("thirty", 30), Map.entry("forty", 40), Map.entry("fifty", 50),
            Map.entry("sixty", 60), Map.entry("seventy", 70), Map.entry("eighty", 80),
            Map.entry("ninety", 90));

    private AudioTranscriptAmountFormatter() { }

    static String format(String transcript) {
        String numeric = replaceSpokenAmounts(transcript);
        numeric = CURRENCY_BEFORE.matcher(numeric).replaceAll("");
        numeric = CURRENCY_AFTER.matcher(numeric).replaceAll("");
        return numeric.replaceAll("[ \\t]{2,}", " ").trim();
    }

    private static String replaceSpokenAmounts(String text) {
        Matcher matcher = SPOKEN_AMOUNT.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String words = matcher.group(2).trim();
            Long amount = parse(words);
            String replacement = amount == null ? matcher.group()
                    : matcher.group(1) + " " + amount
                    + (Character.isWhitespace(matcher.group().charAt(matcher.group().length() - 1)) ? " " : "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static Long parse(String words) {
        long total = 0;
        long part = 0;
        boolean found = false;
        for (String token : words.toLowerCase().split("[\\s-]+")) {
            if (token.isEmpty() || token.equals("and")) continue;
            found = true;
            if (token.equals("hundred")) part *= 100;
            else if (token.equals("thousand")) {
                total += part * 1000;
                part = 0;
            } else {
                Integer value = VALUES.get(token);
                if (value == null) return null;
                part += value;
            }
        }
        return found ? total + part : null;
    }
}
