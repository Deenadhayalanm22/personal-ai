package com.apps.deen_sa.conversation;

import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import com.apps.deen_sa.integration.AbstractIntegrationTestProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("Layer1")
@SpringBootTest
@AutoConfigureMockMvc
class DailyReviewPositiveTest extends AbstractIntegrationTestProperties {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StateContainerRepository stateContainerRepository;

    @Test
    void it_001() throws Exception {
        String expectedResponse = new ClassPathResource("wiremock/it_001/output/account-setup-response.json")
                .getContentAsString(StandardCharsets.UTF_8);
        String expectedCreditCardResponse = new ClassPathResource(
                "wiremock/it_001/output/credit-card-setup-response.json")
                .getContentAsString(StandardCharsets.UTF_8);

        String payload = """
                {
                    "text": "Setup my hdfc bank account where i have currently 40k balance as of now"
                }
                """;

        mockMvc.perform(post("/api/v1/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedResponse));

        String creditCardPayload = """
                {
                    "text": "Setup my hdfc credit card where i have 30k inr limit outstanding of 0 and due date is 21 of every month"
                }
                """;

        mockMvc.perform(post("/api/v1/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creditCardPayload))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedCreditCardResponse));

        assertSpeechResponse(
                "Paid house rent of 16000 via bank transfer",
                "wiremock/it_001/output/rent-expense-response.json");
        assertSpeechResponse(
                "Apartment maintenance charges paid by UPI, 3500",
                "wiremock/it_001/output/maintenance-expense-response.json");
        assertSpeechResponse(
                "Bought a small table for home from IKEA online for 4200 using credit card",
                "wiremock/it_001/output/furniture-expense-response.json");
        assertSpeechResponse(
                "How much did I spend today?",
                "wiremock/it_001/output/today-expense-query-response.json");
        assertSpeechResponse(
                "How much did I spend thsi month?",
                "wiremock/it_001/output/month-expense-query-response.json");

        await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> assertThat(stateContainerRepository.findAll())
                        .hasSize(2)
                        .anySatisfy(this::assertPersistedAccount)
                        .anySatisfy(this::assertPersistedCreditCard));

        assertThat(stateChangeRepository.findAll())
                .hasSize(3)
                .anySatisfy(expense -> {
                    assertThat(expense.getRawText()).isEqualTo("Paid house rent of 16000 via bank transfer");
                    assertThat(expense.getAmount()).isEqualByComparingTo("16000");
                    assertThat(expense.getCategory()).isEqualTo("Housing");
                    assertThat(expense.getSubcategory()).isEqualTo("Rent");
                    assertThat(expense.getSourceContainerId()).isNotNull();
                    assertThat(expense.isFinanciallyApplied()).isTrue();
                    assertThat(expense.isNeedsEnrichment()).isFalse();
                })
                .anySatisfy(expense -> {
                    assertThat(expense.getRawText()).isEqualTo("Apartment maintenance charges paid by UPI, 3500");
                    assertThat(expense.getAmount()).isEqualByComparingTo("3500");
                    assertThat(expense.getSubcategory()).isEqualTo("Maintenance");
                    assertThat(expense.getTags()).containsExactly("apartment");
                    assertThat(expense.getSourceContainerId()).isNotNull();
                    assertThat(expense.isFinanciallyApplied()).isTrue();
                })
                .anySatisfy(expense -> {
                    assertThat(expense.getRawText()).isEqualTo("Bought a small table for home from IKEA online for 4200 using credit card");
                    assertThat(expense.getAmount()).isEqualByComparingTo("4200");
                    assertThat(expense.getSubcategory()).isEqualTo("Furniture");
                    assertThat(expense.getMainEntity()).isEqualTo("IKEA");
                    assertThat(expense.getTags()).containsExactly("home", "table", "online");
                    assertThat(expense.getSourceContainerId()).isNotNull();
                    assertThat(expense.isFinanciallyApplied()).isTrue();
                    assertThat(expense.isNeedsEnrichment()).isFalse();
                });
    }

    private void assertSpeechResponse(String text, String expectedResponsePath) throws Exception {
        String expectedResponse = new ClassPathResource(expectedResponsePath)
                .getContentAsString(StandardCharsets.UTF_8);
        String payload = """
                {"text": "%s"}
                """.formatted(text);

        mockMvc.perform(post("/api/v1/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedResponse));
    }

    private void assertPersistedAccount(StateContainerEntity account) {
        assertThat(account.getId()).isPositive();
        assertThat(account.getOwnerType()).isEqualTo("USER");
        assertThat(account.getOwnerId()).isEqualTo(1L);
        assertThat(account.getContainerType()).isEqualTo("BANK_ACCOUNT");
        assertThat(account.getName()).isEqualTo("hdfc bank account");
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getCurrency()).isEqualTo("INR");
        assertThat(account.getCurrentValue()).isEqualByComparingTo(new BigDecimal("20500"));
        assertThat(account.getAvailableValue()).isEqualByComparingTo(new BigDecimal("20500"));
        assertThat(account.getUnit()).isNull();
        assertThat(account.getCapacityLimit()).isNull();
        assertThat(account.getMinThreshold()).isNull();
        assertThat(account.getPriorityOrder()).isNull();
        assertThat(account.getOpenedAt()).isNotNull();
        assertThat(account.getClosedAt()).isNull();
        assertThat(account.getLastActivityAt()).isNotNull();
        assertThat(account.getExternalRefType()).isNull();
        assertThat(account.getExternalRefId()).isEqualTo("hdfc bank account");
        assertThat(account.getDetails()).isNull();
        assertThat(account.getCreatedAt()).isNotNull();
        assertThat(account.getUpdatedAt()).isNotNull();
        assertThat(account.getOverLimit()).isFalse();
        assertThat(account.getOverLimitAmount()).isNull();
    }

    private void assertPersistedCreditCard(StateContainerEntity account) {
        assertThat(account.getId()).isPositive();
        assertThat(account.getOwnerType()).isEqualTo("USER");
        assertThat(account.getOwnerId()).isEqualTo(1L);
        assertThat(account.getContainerType()).isEqualTo("CREDIT_CARD");
        assertThat(account.getName()).isEqualTo("hdfc credit card");
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getCurrency()).isEqualTo("INR");
        assertThat(account.getCurrentValue()).isEqualByComparingTo(new BigDecimal("4200"));
        assertThat(account.getAvailableValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getCapacityLimit()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(account.getDetails()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("dueDay", 21));
        assertThat(account.getExternalRefId()).isEqualTo("hdfc credit card");
        assertThat(account.getOverLimit()).isFalse();
        assertThat(account.getOverLimitAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

}
