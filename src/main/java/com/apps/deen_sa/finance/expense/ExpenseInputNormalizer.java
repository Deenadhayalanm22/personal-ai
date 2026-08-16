package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.dto.ExpenseDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ExpenseInputNormalizer {
    private final ExpenseCategoryResolver categoryResolver;

    public ExpenseInputNormalizer(ExpenseCategoryResolver categoryResolver) { this.categoryResolver = categoryResolver; }

    private static final Pattern EXPLICIT_SPEND_AMOUNT = Pattern.compile(
            "(?i)\\b(?:spent|paid)\\s*(?:rs\\.?|inr|₹)?\\s*"
                    + "([0-9][0-9,]*(?:\\.[0-9]+)?\\s*(?:k|thousand|lakh|lac|crore|cr)?)\\b");

    public ExpenseDto normalize(ExpenseDto dto, String originalText, ConversationContext context) {
        if (dto == null) dto = new ExpenseDto();
        dto.setRawText(originalText);

        LocalDate today = LocalDate.now(resolveZone(context.getTimezone()));
        if (containsWord(originalText, "yesterday")) dto.setTransactionDate(today.minusDays(1));
        else if (containsWord(originalText, "today")) dto.setTransactionDate(today);
        else if (dto.getTransactionDate() == null) dto.setTransactionDate(today);
        if (dto.getAmount() == null) {
            explicitAmount(originalText).ifPresent(dto::setAmount);
        }
        dto.setSourceAccount(canonicalPaymentSource(dto.getSourceAccount()));
        categoryResolver.canonicalize(dto, originalText);
        return dto;
    }

    private String canonicalPaymentSource(String source) {
        if (source == null || source.isBlank()) return source;
        String normalized = source.trim();
        // Extraction patterns may capture temporal/purpose words following a
        // generic payment rail. Those words are not part of the account name.
        if (normalized.matches("(?i)^(?:my\\s+)?upi\\b.*$")) return "UPI";
        if (normalized.matches("(?i)^(?:my\\s+)?(?:bank\\s*[/&]\\s*upi|bank\\s+upi)\\b.*$"))
            return "BANK_ACCOUNT";
        return normalized;
    }

    private boolean containsWord(String text, String word) {
        return text != null && Pattern.compile("(?i)(?:^|[^\\p{L}])" + Pattern.quote(word)
                + "(?:$|[^\\p{L}])").matcher(text).find();
    }

    private java.util.Optional<BigDecimal> explicitAmount(String text) {
        if (text == null) return java.util.Optional.empty();
        Matcher matcher = EXPLICIT_SPEND_AMOUNT.matcher(text);
        if (!matcher.find()) return java.util.Optional.empty();
        return HumanAmountParser.parse(matcher.group(1));
    }

    private ZoneId resolveZone(String timezone) {
        try {
            return ZoneId.of(timezone == null ? "Asia/Kolkata" : timezone);
        } catch (RuntimeException invalidTimezone) {
            return ZoneId.of("Asia/Kolkata");
        }
    }
}
