package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.extension.api.DeterministicEventRouter;
import com.apps.deen_sa.extension.api.DeterministicEventCandidate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Routes only syntax whose intent is mechanically certain; enrichment remains model-driven. */
final class FinanceDeterministicEventRouter implements DeterministicEventRouter {
    private static final Pattern SPARSE_EXPENSE = Pattern.compile(
            "(?i)^\\s*(?:i\\s+)?(?:spent|paid)\\s*(?:₹|rs\\.?|inr)?\\s*"
                    + "[0-9][0-9,]*(?:\\.[0-9]+)?(?:\\s*(?:k|thousand|lakh|lac|crore|cr))?\\s*[.!]?\\s*$");
    private static final Pattern TWO_EXPENSES = Pattern.compile(
            "(?i)^\\s*(?:i\\s+)?(?:spent|paid)\\s+(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+on\\s+(.+?)\\s+and\\s+(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+on\\s+(.+?)\\s+(?:using|through|via)\\s+(.+?)\\s*[.!]?\\s*$");
    private static final Pattern SPENT_ON = Pattern.compile(
            "(?i)^\\s*(?:i\\s+)?(?:spent|spend|paid|add|record)\\s+(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+on\\s+(.+?)(?:\\s+(?:using|through|via)\\s+(.+?))?\\s*[.!]?\\s*$");
    private static final Pattern VERB_DESCRIPTION_AMOUNT = Pattern.compile(
            "(?i)^\\s*(?:i\\s+)?(?:spent|spend|paid|add|record)\\s+(?:for\\s+)?(.+?)\\s+"
                    + "(?:for\\s+)?(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*[.!]?\\s*$");
    private static final Pattern SPENT_TODAY_FROM = Pattern.compile(
            "(?i)^\\s*(?:i\\s+)?spent\\s+(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+on\\s+(.+?)\\s+today\\s+from\\s+(.+?)\\s*[.!]?\\s*$");
    private static final Pattern AMOUNT_FOR_DESCRIPTION_ON_DAY_FROM = Pattern.compile(
            "(?i)^\\s*(?:add|record)\\s+(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+for\\s+(.+?)\\s+on\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)"
                    + "\\s+from\\s+(.+?)\\s*[.!]?\\s*$");
    private static final Pattern PERSON_PAYMENT = Pattern.compile(
            "(?i)^\\s*(?:i\\s+)?(?:transferred|transfer|sent|send|paid)\\s+"
                    + "(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+to\\s+"
                    + "(?:my\\s+)?(.+?)\\s+(?:paid\\s+)?(?:using|through|via|from)\\s+(.+?)\\s*[.!]?\\s*$");
    private static final Pattern PAID_FOR = Pattern.compile(
            "(?i)^\\s*paid\\s+(.+?)\\s+(?:of|for)\\s+(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "(?:\\s+(?:using|through|via)\\s+(.+?))?\\s*[.!]?\\s*$");
    private static final Pattern DESCRIPTION_FOR_AMOUNT_WITH_SOURCE = Pattern.compile(
            "(?i)^\\s*(.+?)\\s+for\\s+(?:₹|rs\\.?|inr)?\\s*"
                    + "([0-9][0-9,]*(?:\\.[0-9]+)?(?:\\s*(?:k|thousand|lakh|lac|crore|cr))?)"
                    + "\\s+(?:paid\\s+)?(?:using|through|via)\\s+(.+?)\\s*[.!]?\\s*$");
    private static final Pattern DESCRIPTION_OF_AMOUNT_WITH_SOURCE = Pattern.compile(
            "(?i)^\\s*(.+?)\\s+of\\s+(?:₹|rs\\.?|inr)?\\s*"
                    + "([0-9][0-9,]*(?:\\.[0-9]+)?(?:\\s*(?:k|thousand|lakh|lac|crore|cr))?)"
                    + "\\s+paid\\s+(?:using|through|via)\\s+(.+?)\\s*[.!]?\\s*$");
    private static final Pattern DATED_PURCHASE = Pattern.compile(
            "(?i)^\\s*on\\s+([0-9]{1,2}\\s+[a-z]+\\s+[0-9]{4})\\s+i\\s+(?:purchased|bought)\\s+(.+?)"
                    + "\\s+for\\s+(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+(?:using|through|via)\\s+(.+?)\\s*[.!]?\\s*$");
    private static final Pattern DATED_SALARY_CREDIT = Pattern.compile(
            "(?i)^\\s*my\\s+[a-z]+\\s+salary\\s+of\\s+(?:₹|rs\\.?|inr)?\\s*"
                    + "([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+was\\s+credited\\s+to\\s+my\\s+(.+?)"
                    + "\\s+on\\s+([0-9]{1,2}\\s+[a-z]+\\s+[0-9]{4})\\s*[.!]?\\s*$");
    private static final Pattern TANGLISH_COMPLETE = Pattern.compile(
            "(?i)^\\s*(?:inniku|nethu)\\s+(.+?)\\s+k(?:u|ku)\\s+([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+(?:rupees?|rs\\.?)\\s+(.+?)\\s+la\\s+(?:spend|spent|pay|paid)\\s+pannen\\s*[.!]?\\s*$");
    private static final Pattern TANGLISH_MISSING_AMOUNT = Pattern.compile(
            "(?i)^\\s*(?:inniku|nethu)\\s+(.+?)\\s+k(?:u|ku)\\s+(?:spend|spent|pay|paid)\\s+pannen\\s*[.!]?\\s*$");
    private static final Pattern TAMIL_WITH_SOURCE = Pattern.compile(
            "^\\s*(?:இன்று|நேற்று)\\s+(.+?)\\s+([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+ரூபாய்\\s+(.+?)\\s+மூலம்\\s+செலவு\\s+செய்தேன்\\s*[.!]?\\s*$");
    private static final Pattern TAMIL_WITHOUT_SOURCE = Pattern.compile(
            "^\\s*(?:இன்று|நேற்று)\\s+(.+?)\\s+([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+ரூபாய்\\s+செலவு\\s+செய்தேன்\\s*[.!]?\\s*$");
    private static final Pattern EXPENSE_SUMMARY_QUERY = Pattern.compile(
            "(?i)^.*\\b(?:summary|breakdown|total|spent|spending)\\b.*"
                    + "\\b(?:today|this\\s+week|this\\s+month|this\\s+year|last\\s+month|last\\s+(?:3|three)\\s+months)\\b.*$|"
                    + "^.*\\b(?:today|this\\s+week|this\\s+month|this\\s+year|last\\s+month|last\\s+(?:3|three)\\s+months)\\b.*"
                    + "\\b(?:summary|breakdown|total|spent|spending)\\b.*$");
    private static final Pattern GENERAL_EXPENSE_SUMMARY_QUERY = Pattern.compile(
            "(?i)^.*(?:\\b(?:summary|breakdown)\\b|\\bspending\\s+patterns?\\b).*$");

    @Override
    public Optional<String> eventType(String text) {
        if (text == null) return Optional.empty();
        // Corrections require multilingual semantic extraction of action and category scope.
        // They intentionally go through the unified interpreter.
        return SPARSE_EXPENSE.matcher(text).matches() ? Optional.of("EXPENSE") : Optional.empty();
    }

    @Override
    public List<DeterministicEventCandidate> events(String text) {
        if (text == null) return List.of();
        Matcher income = DATED_SALARY_CREDIT.matcher(text);
        if (income.matches()) return List.of(datedIncomeCandidate(
                income.group(1), income.group(2), income.group(3), text));
        Matcher matcher = TWO_EXPENSES.matcher(text);
        if (matcher.matches()) {
            String source = matcher.group(5).trim();
            return List.of(candidate(matcher.group(1), matcher.group(2), source, text),
                    candidate(matcher.group(3), matcher.group(4), source, text));
        }
        matcher = SPENT_TODAY_FROM.matcher(text);
        if (matcher.matches())
            return List.of(candidate(matcher.group(1), matcher.group(2), matcher.group(3), text));
        matcher = AMOUNT_FOR_DESCRIPTION_ON_DAY_FROM.matcher(text);
        if (matcher.matches())
            return List.of(candidate(matcher.group(1), matcher.group(2), matcher.group(4), text));
        matcher = PERSON_PAYMENT.matcher(text);
        if (matcher.matches())
            return List.of(candidate(matcher.group(1), matcher.group(2), matcher.group(3), text));
        matcher = SPENT_ON.matcher(text);
        if (matcher.matches())
            return List.of(candidate(matcher.group(1), matcher.group(2), matcher.group(3), text));
        matcher = VERB_DESCRIPTION_AMOUNT.matcher(text);
        if (matcher.matches())
            return List.of(candidate(matcher.group(2), matcher.group(1), null, text));
        matcher = DATED_PURCHASE.matcher(text);
        if (matcher.matches())
            return List.of(datedCandidate(matcher.group(3), matcher.group(2), matcher.group(4), matcher.group(1), text));
        matcher = PAID_FOR.matcher(text);
        if (matcher.matches())
            return List.of(candidate(matcher.group(2), matcher.group(1), matcher.group(3), text));
        matcher = DESCRIPTION_FOR_AMOUNT_WITH_SOURCE.matcher(text);
        if (matcher.matches())
            return List.of(candidate(humanAmount(matcher.group(2)).toPlainString(), matcher.group(1), matcher.group(3), text));
        matcher = DESCRIPTION_OF_AMOUNT_WITH_SOURCE.matcher(text);
        if (matcher.matches())
            return List.of(candidate(humanAmount(matcher.group(2)).toPlainString(), matcher.group(1), matcher.group(3), text));
        matcher = TANGLISH_COMPLETE.matcher(text);
        if (matcher.matches())
            return List.of(candidate(matcher.group(2), matcher.group(1), matcher.group(3), text));
        matcher = TANGLISH_MISSING_AMOUNT.matcher(text);
        if (matcher.matches()) return List.of(candidateWithoutAmount(matcher.group(1), text));
        matcher = TAMIL_WITH_SOURCE.matcher(text);
        if (matcher.matches())
            return List.of(candidate(matcher.group(2), matcher.group(1), matcher.group(3), text));
        matcher = TAMIL_WITHOUT_SOURCE.matcher(text);
        if (matcher.matches())
            return List.of(candidate(matcher.group(2), matcher.group(1), null, text));
        return List.of();
    }

    @Override
    public Optional<String> query(String text) {
        if (text == null) return Optional.empty();
        String query = text.trim().toLowerCase(Locale.ROOT);
        if (EXPENSE_SUMMARY_QUERY.matcher(query).matches()) {
            if (query.contains("today") || query.contains("today's")) return Optional.of("TODAY");
            if (query.contains("this week")) return Optional.of("THIS_WEEK");
            if (query.contains("this year")) return Optional.of("THIS_YEAR");
            if (query.contains("last month")) return Optional.of("LAST_MONTH");
            if (query.contains("last 3 months") || query.contains("last three months")) return Optional.of("LAST_3_MONTHS");
            return Optional.of("THIS_MONTH");
        }
        if (GENERAL_EXPENSE_SUMMARY_QUERY.matcher(query).matches()) return Optional.of("THIS_MONTH");
        // Other spending questions require semantic period interpretation.
        return Optional.empty();
    }

    private DeterministicEventCandidate candidate(String amount, String description, String source, String rawText) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("amount", new BigDecimal(amount.replace(",", "")));
        fields.put("merchantName", description.trim());
        if (source != null && !source.isBlank()) fields.put("sourceAccount", source.trim());
        fields.put("rawText", rawText);
        return new DeterministicEventCandidate("EXPENSE", fields);
    }

    private DeterministicEventCandidate candidateWithoutAmount(String description, String rawText) {
        return new DeterministicEventCandidate("EXPENSE", Map.of(
                "merchantName", description.trim(), "rawText", rawText));
    }

    private DeterministicEventCandidate datedCandidate(
            String amount, String description, String source, String date, String rawText) {
        Map<String, Object> fields = new LinkedHashMap<>(candidate(amount, description, source, rawText).fields());
        fields.put("transactionDate", LocalDate.parse(date, DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH)));
        return new DeterministicEventCandidate("EXPENSE", fields);
    }

    private DeterministicEventCandidate datedIncomeCandidate(
            String amount, String destination, String date, String rawText) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("amount", new BigDecimal(amount.replace(",", "")));
        fields.put("category", "Income");
        fields.put("subcategory", "Salary");
        fields.put("destinationAccount", destination.trim());
        fields.put("transactionDate", LocalDate.parse(
                date, DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH)));
        fields.put("rawText", rawText);
        return new DeterministicEventCandidate("INCOME", fields);
    }

    private BigDecimal humanAmount(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT).replace(",", "").replaceAll("\\s+", "");
        BigDecimal multiplier = BigDecimal.ONE;
        if (normalized.endsWith("k")) { multiplier = BigDecimal.valueOf(1_000); normalized = normalized.substring(0, normalized.length() - 1); }
        else if (normalized.endsWith("thousand")) { multiplier = BigDecimal.valueOf(1_000); normalized = normalized.replace("thousand", ""); }
        else if (normalized.endsWith("lakh") || normalized.endsWith("lac")) {
            multiplier = BigDecimal.valueOf(100_000); normalized = normalized.replaceAll("(?:lakh|lac)$", "");
        }
        return new BigDecimal(normalized).multiply(multiplier);
    }

}
