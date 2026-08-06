package com.apps.deen_sa.conversation;

import com.apps.deen_sa.dto.ExpenseSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMessagesTest {
    private final ConversationMessages messages = new ConversationMessages();

    @Test
    void rendersTamilSummaryWithoutModelGeneration() {
        ExpenseSummary summary = new ExpenseSummary();
        summary.setTotalSpend(new BigDecimal("558.00"));

        assertThat(messages.summary("ta-IN", "TODAY", summary))
                .isEqualTo("இன்று உங்கள் மொத்த செலவு ₹558.");
    }

    @Test
    void keepsRomanizedTamilOnEnglishFallbackUntilUserChoosesTamilScript() {
        ExpenseSummary summary = new ExpenseSummary();
        summary.setTotalSpend(new BigDecimal("558.00"));

        assertThat(messages.summary("ta-Latn", "TODAY", summary))
                .isEqualTo("You spent a total of ₹558 today.");
    }
}
