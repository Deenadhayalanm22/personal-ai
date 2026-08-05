package com.apps.deen_sa.conversation;

import com.apps.deen_sa.core.state.CompletenessLevelEnum;
import com.apps.deen_sa.integration.AbstractIntegrationTestProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("Layer1")
@SpringBootTest
@AutoConfigureMockMvc
class DailyReviewNegativeTest extends AbstractIntegrationTestProperties {

    private static final String PHONE = "919876543210";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationSessionRepository sessionRepository;

    @Value("${wiremock.admin-url:http://localhost:9091/__admin}")
    private String wireMockAdminUrl;

    @Test
    void it_neg_001() throws Exception {
        sendText("wamid.expense-1", "I spent 500");

        awaitState(() -> {
            assertThat(stateChangeRepository.findAll()).singleElement().satisfies(expense -> {
                assertThat(expense.getAmount()).isEqualByComparingTo("500");
                assertThat(expense.getCategory()).isNull();
                assertThat(expense.getSourceContainerId()).isNull();
                assertThat(expense.isFinanciallyApplied()).isFalse();
                assertThat(expense.isNeedsEnrichment()).isTrue();
            });
            assertWaitingFor("category");
        });
        awaitState(this::assertCategorySuggestionsSent);

        sendText("wamid.expense-2", "Groceries");

        awaitState(() -> {
            assertThat(stateChangeRepository.findAll()).singleElement().satisfies(expense -> {
                assertThat(expense.getCategory()).isEqualTo("Food & Dining");
                assertThat(expense.getSubcategory()).isEqualTo("Groceries");
            });
            assertWaitingFor("sourceAccount");
        });

        sendButton("wamid.expense-3", "answer:BANK_ACCOUNT", "Bank / UPI");

        awaitState(() -> {
            assertThat(stateContainerRepository.findAll()).singleElement().satisfies(account -> {
                assertThat(account.getOwnerType()).isEqualTo("USER");
                assertThat(account.getContainerType()).isEqualTo("BANK_ACCOUNT");
                assertThat(account.getName()).isEqualTo("My bank account");
                assertThat(account.getCurrentValue()).isNull();
            });
            assertThat(stateChangeRepository.findAll()).singleElement().satisfies(expense -> {
                assertThat(expense.getSourceContainerId()).isNotNull();
                assertThat(expense.isFinanciallyApplied()).isFalse();
            });
            assertWaitingFor("sourceBalance");
        });

        sendText("wamid.expense-4", "10000");

        awaitState(() -> {
            assertThat(stateContainerRepository.findAll()).singleElement().satisfies(account -> {
                assertThat(account.getCurrentValue()).isEqualByComparingTo("9500");
                assertThat(account.getAvailableValue()).isEqualByComparingTo("9500");
                assertThat(account.getLastActivityAt()).isNotNull();
            });
            assertThat(stateChangeRepository.findAll()).singleElement().satisfies(expense -> {
                assertThat(expense.getCompletenessLevel()).isEqualTo(CompletenessLevelEnum.FINANCIAL);
                assertThat(expense.isFinanciallyApplied()).isTrue();
                assertThat(expense.isNeedsEnrichment()).isFalse();
            });
            assertThat(stateMutationRepository.findAll()).singleElement().satisfies(mutation ->
                    assertThat(mutation.getAmount()).isEqualByComparingTo("500"));
            assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
                assertThat(session.getActiveIntent()).isNull();
                assertThat(session.getWaitingForField()).isNull();
                assertThat(session.getActiveTransactionId()).isNull();
            });
        });

        // Meta may redeliver a webhook. The external message ID must prevent a duplicate expense.
        sendText("wamid.expense-1", "I spent 500");
        awaitState(() -> {
            assertThat(stateChangeRepository.findAll()).hasSize(1);
            assertThat(stateMutationRepository.findAll()).hasSize(1);
        });
    }

    private void sendText(String messageId, String text) throws Exception {
        String payload = """
                {
                  "entry": [{"changes": [{"value": {"messages": [{
                    "id": "%s",
                    "from": "%s",
                    "type": "text",
                    "text": {"body": "%s"}
                  }]}}]}]
                }
                """.formatted(messageId, PHONE, text);
        postWebhook(payload);
    }

    private void sendButton(String messageId, String buttonId, String title) throws Exception {
        String payload = """
                {
                  "entry": [{"changes": [{"value": {"messages": [{
                    "id": "%s",
                    "from": "%s",
                    "type": "interactive",
                    "interactive": {
                      "type": "button_reply",
                      "button_reply": {"id": "%s", "title": "%s"}
                    }
                  }]}}]}]
                }
                """.formatted(messageId, PHONE, buttonId, title);
        postWebhook(payload);
    }

    private void postWebhook(String payload) throws Exception {
        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private void assertWaitingFor(String field) {
        assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
            assertThat(session.getActiveIntent()).isEqualTo("EXPENSE");
            assertThat(session.getWaitingForField()).isEqualTo(field);
            assertThat(session.getActiveTransactionId()).isNotNull();
        });
    }

    private void assertCategorySuggestionsSent() {
        String requestJournal = RestClient.create(wireMockAdminUrl)
                .get()
                .uri("/requests")
                .retrieve()
                .body(String.class);

        assertThat(requestJournal)
                .contains("Reply with something like groceries, fuel, rent")
                .contains("Groceries")
                .contains("Food / Dining")
                .contains("Travel")
                .doesNotContain("Skip for now");
    }

    private void awaitState(Runnable assertion) {
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(assertion::run);
    }
}
