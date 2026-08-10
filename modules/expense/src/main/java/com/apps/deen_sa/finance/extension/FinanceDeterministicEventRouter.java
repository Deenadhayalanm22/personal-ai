package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.extension.api.DeterministicEventRouter;
import com.apps.deen_sa.extension.api.DeterministicEventCandidate;

import java.math.BigDecimal;
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
    private static final Pattern ACCOUNT_SETUP = Pattern.compile(
            "(?i)^\\s*create\\s+my\\s+.+?\\s+(?:bank\\s+account|credit\\s+card)\\s+with\\s+.+$");
    private static final Pattern BUDGET_SET = Pattern.compile(
            "(?i)^\\s*(?:set|keep)\\s+(?:my\\s+)?(?:monthly\\s+)?(.+?)\\s+budget\\s+(?:to|at|as)\\s*"
                    + "(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?(?:\\s*(?:k|thousand|lakh|lac))?)\\s*[.!]?\\s*$");
    private static final Pattern MONTHLY_SCOPE_BALANCE_SET = Pattern.compile(
            "(?i)^\\s*(?:my\\s+)?(.+?)\\s+(?:balance|budget|limit)\\s+for\\s+(?:this|the)\\s+month\\s+"
                    + "(?:is|is\\s+only|should\\s+be|=)\\s*(?:₹|rs\\.?|inr)?\\s*"
                    + "([0-9][0-9,]*(?:\\.[0-9]+)?(?:\\s*(?:k|thousand|lakh|lac))?)\\s*[.!]?\\s*$");
    private static final Pattern BUDGET_QUERY = Pattern.compile(
            "(?i)^.*\\b(?:budget|planned)\\b.*(?:how\\s+much|status|doing|remaining|remain|left|against).*$|"
                    + "^.*(?:how\\s+much|status|doing|remaining|remain|left|against).*\\b(?:budget|planned)\\b.*$");
    private static final Pattern TWO_EXPENSES = Pattern.compile(
            "(?i)^\\s*(?:i\\s+)?(?:spent|paid)\\s+(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+on\\s+(.+?)\\s+and\\s+(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+on\\s+(.+?)\\s+(?:using|through|via)\\s+(.+?)\\s*[.!]?\\s*$");
    private static final Pattern SPENT_ON = Pattern.compile(
            "(?i)^\\s*(?:i\\s+)?(?:spent|paid)\\s+(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+on\\s+(.+?)(?:\\s+(?:using|through|via)\\s+(.+?))?\\s*[.!]?\\s*$");
    private static final Pattern SPENT_TODAY_FROM = Pattern.compile(
            "(?i)^\\s*(?:i\\s+)?spent\\s+(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+on\\s+(.+?)\\s+today\\s+from\\s+(.+?)\\s*[.!]?\\s*$");
    private static final Pattern PAID_FOR = Pattern.compile(
            "(?i)^\\s*paid\\s+(.+?)\\s+(?:of|for)\\s+(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "(?:\\s+(?:using|through|via)\\s+(.+?))?\\s*[.!]?\\s*$");
    private static final Pattern TANGLISH_COMPLETE = Pattern.compile(
            "(?i)^\\s*(?:inniku|nethu)\\s+(.+?)\\s+k(?:u|ku)\\s+([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+(?:rupees?|rs\\.?)\\s+(.+?)\\s+la\\s+(?:spend|spent|pay|paid)\\s+pannen\\s*[.!]?\\s*$");
    private static final Pattern TANGLISH_MISSING_AMOUNT = Pattern.compile(
            "(?i)^\\s*(?:inniku|nethu)\\s+(.+?)\\s+k(?:u|ku)\\s+(?:spend|spent|pay|paid)\\s+pannen\\s*[.!]?\\s*$");
    private static final Pattern TAMIL_WITH_SOURCE = Pattern.compile(
            "^\\s*(?:இன்று|நேற்று)\\s+(.+?)\\s+([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+ரூபாய்\\s+(.+?)\\s+மூலம்\\s+செலவு\\s+செய்தேன்\\s*[.!]?\\s*$");
    private static final Pattern TAMIL_WITHOUT_SOURCE = Pattern.compile(
            "^\\s*(?:இன்று|நேற்று)\\s+(.+?)\\s+([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+ரூபாய்\\s+செலவு\\s+செய்தேன்\\s*[.!]?\\s*$");

    @Override
    public Optional<String> eventType(String text) {
        if (text == null) return Optional.empty();
        if (ACCOUNT_SETUP.matcher(text).matches()) return Optional.of("ACCOUNT_SETUP");
        if (BUDGET_SET.matcher(text).matches() || MONTHLY_SCOPE_BALANCE_SET.matcher(text).matches())
            return Optional.of("BUDGET_SET");
        return SPARSE_EXPENSE.matcher(text).matches() ? Optional.of("EXPENSE") : Optional.empty();
    }

    @Override
    public List<DeterministicEventCandidate> events(String text) {
        if (text == null) return List.of();
        Matcher budget = BUDGET_SET.matcher(text);
        if (budget.matches()) return List.of(budgetCandidate(budget.group(1), budget.group(2), text));
        budget = MONTHLY_SCOPE_BALANCE_SET.matcher(text);
        if (budget.matches()) return List.of(budgetCandidate(budget.group(1), budget.group(2), text));
        Matcher matcher = TWO_EXPENSES.matcher(text);
        if (matcher.matches()) {
            String source = matcher.group(5).trim();
            return List.of(candidate(matcher.group(1), matcher.group(2), source, text),
                    candidate(matcher.group(3), matcher.group(4), source, text));
        }
        matcher = SPENT_TODAY_FROM.matcher(text);
        if (matcher.matches())
            return List.of(candidate(matcher.group(1), matcher.group(2), matcher.group(3), text));
        matcher = SPENT_ON.matcher(text);
        if (matcher.matches())
            return List.of(candidate(matcher.group(1), matcher.group(2), matcher.group(3), text));
        matcher = PAID_FOR.matcher(text);
        if (matcher.matches())
            return List.of(candidate(matcher.group(2), matcher.group(1), matcher.group(3), text));
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
        return BUDGET_QUERY.matcher(text.trim()).matches() ? Optional.of("CURRENT_STATUS") : Optional.empty();
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

    private DeterministicEventCandidate budgetCandidate(String scope, String amount, String rawText) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("category", scope.trim());
        fields.put("amount", humanAmount(amount));
        fields.put("rawText", rawText);
        return new DeterministicEventCandidate("BUDGET_SET", fields);
    }
}
