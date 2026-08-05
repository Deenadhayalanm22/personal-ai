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
}
