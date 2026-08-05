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

    private static final Pattern EXPLICIT_SPEND_AMOUNT = Pattern.compile(
            "(?i)\\b(?:spent|paid)\\s*(?:rs\\.?|inr|₹)?\\s*"
                    + "([0-9][0-9,]*(?:\\.[0-9]+)?\\s*(?:k|thousand|lakh|lac|crore|cr)?)\\b");

    public ExpenseDto normalize(ExpenseDto dto, String originalText, ConversationContext context) {
        if (dto == null) dto = new ExpenseDto();
        dto.setRawText(originalText);

        if (dto.getTransactionDate() == null) {
            dto.setTransactionDate(LocalDate.now(resolveZone(context.getTimezone())));
        }
        if (dto.getAmount() == null) {
            explicitAmount(originalText).ifPresent(dto::setAmount);
        }
        return dto;
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
