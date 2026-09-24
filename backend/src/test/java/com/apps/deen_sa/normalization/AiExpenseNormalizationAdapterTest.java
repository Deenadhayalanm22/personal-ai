package com.apps.deen_sa.normalization;

import com.apps.deen_sa.config.ApplicationProperties;
import com.apps.deen_sa.repository.UserReferenceAliasRepository;
import com.apps.deen_sa.repository.UserReferenceEntityRepository;
import com.apps.deen_sa.service.ExpenseTaxonomyRegistry;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiExpenseNormalizationAdapterTest {

    @Test
    void classifiesTanglishInputWithEnglishTaxonomyLabels() {
        var properties = new ApplicationProperties(
                new ApplicationProperties.OpenAi("key", "https://api.openai.com/v1",
                        "gpt-4.1-mini", "gpt-4.1-mini", 0.55, "gpt-4o-mini-transcribe"),
                new ApplicationProperties.WhatsApp("key", "phone", "https://graph.facebook.com"));
        var adapter = new AiExpenseNormalizationAdapter(mock(OpenAIClient.class), properties,
                new ExpenseTaxonomyRegistry(), mock(UserReferenceEntityRepository.class),
                mock(UserReferenceAliasRepository.class)) {
            @Override
            protected <T> T callAndParse(String systemPrompt, String userPrompt, Class<T> responseType) {
                assertThat(systemPrompt).contains("Tanglish", "classification labels in English");
                assertThat(userPrompt).contains("KK kadai la 25 selavu panninen");
                return responseType.cast(new ExpenseNormalizationPort.ExpenseFacts(
                        new BigDecimal("25"), "Food & Dining", "Groceries", "KK kadai",
                        "Cash", LocalDate.of(2026, 9, 25), new BigDecimal("0.95")));
            }
        };

        var facts = adapter.normalize("9198", "KK kadai la 25 selavu panninen",
                LocalDate.of(2026, 9, 25));

        assertThat(facts.category()).isEqualTo("Food & Dining");
        assertThat(facts.subcategory()).isEqualTo("Groceries");
    }

    @Test
    void doesNotGuessBetweenMultipleCardsForAGenericReference() {
        assertThat(AiExpenseNormalizationAdapter.resolveGenericCreditCardReference(
                "Paid 300 for dinner using credit card",
                List.of("HDFC credit card", "ICICI credit card")))
                .isNull();
    }

    @Test
    void resolvesAGenericReferenceWhenThereIsOnlyOneCard() {
        assertThat(AiExpenseNormalizationAdapter.resolveGenericCreditCardReference(
                "Paid 500 for groceries using credit card",
                List.of("HDFC credit card")))
                .isEqualTo("HDFC credit card");
    }

    @Test
    void doesNotTreatANamedCardAsAGenericReference() {
        assertThat(AiExpenseNormalizationAdapter.hasGenericCreditCardReference(
                "Paid 700 for fuel using ICICI credit card"))
                .isFalse();
    }
}
