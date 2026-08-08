package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.dto.ExpenseDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseInputNormalizerTest {

    private final ExpenseInputNormalizer normalizer = new ExpenseInputNormalizer();

    @Test
    void suppliesDeterministicAmountAndTodayWhenLlmOmitsThem() {
        ConversationContext context = new ConversationContext();
        context.setTimezone("Asia/Kolkata");

        ExpenseDto normalized = normalizer.normalize(new ExpenseDto(), "I spent 500", context);

        assertThat(normalized.getAmount()).isEqualByComparingTo("500");
        assertThat(normalized.getTransactionDate()).isEqualTo(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
        assertThat(normalized.getRawText()).isEqualTo("I spent 500");
        assertThat(normalized.getCategory()).isNull();
    }

    @Test
    void doesNotGuessAnAmountFromUnrelatedNumbers() {
        ExpenseDto normalized = normalizer.normalize(
                new ExpenseDto(), "Invoice 500 arrived but I have not paid it", new ConversationContext());

        assertThat(normalized.getAmount()).isNull();
    }

    @Test
    void understandsCompactIndianAmounts() {
        assertThat(HumanAmountParser.parse("40k")).contains(new java.math.BigDecimal("40000"));
        assertThat(HumanAmountParser.parse("1.5 lakh")).contains(new java.math.BigDecimal("150000.0"));
        assertThat(HumanAmountParser.parse("2 crore")).contains(new java.math.BigDecimal("20000000"));
    }
}
