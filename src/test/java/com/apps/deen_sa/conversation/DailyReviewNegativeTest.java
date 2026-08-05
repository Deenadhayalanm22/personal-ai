package com.apps.deen_sa.conversation;

import com.apps.deen_sa.core.state.CompletenessLevelEnum;
import com.apps.deen_sa.integration.AbstractIntegrationTestProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.apps.deen_sa.conversation.interpretation.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("Layer1")
@SpringBootTest(properties = "conversation.mode=active")
@AutoConfigureMockMvc
@Import(DailyReviewNegativeTest.InterpreterFixture.class)
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
        sendText("wamid.expense-1", "I spent 35");

        awaitState(() -> {
            assertThat(stateChangeRepository.findAll()).singleElement().satisfies(expense -> {
                assertThat(expense.getAmount()).isEqualByComparingTo("35");
                assertThat(expense.getCategory()).isNull();
                assertThat(expense.getSourceContainerId()).isNull();
                assertThat(expense.isFinanciallyApplied()).isFalse();
                assertThat(expense.isNeedsEnrichment()).isTrue();
            });
            assertWaitingFor("category");
        });
        awaitState(this::assertCategorySuggestionsSent);

        sendText("wamid.expense-2", "It is for evening snacks");

        awaitState(() -> {
            assertThat(stateChangeRepository.findAll()).singleElement().satisfies(expense -> {
                assertThat(expense.getCategory()).isEqualTo("Food & Dining");
                assertThat(expense.getSubcategory()).isEqualTo("Snacks & Beverages");
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

        sendText("wamid.expense-4", "40k");

        awaitState(() -> {
            assertThat(stateContainerRepository.findAll()).singleElement().satisfies(account -> {
                assertThat(account.getCurrentValue()).isEqualByComparingTo("39965");
                assertThat(account.getAvailableValue()).isEqualByComparingTo("39965");
                assertThat(account.getLastActivityAt()).isNotNull();
            });
            assertThat(stateChangeRepository.findAll()).singleElement().satisfies(expense -> {
                assertThat(expense.getCompletenessLevel()).isEqualTo(CompletenessLevelEnum.FINANCIAL);
                assertThat(expense.isFinanciallyApplied()).isTrue();
                assertThat(expense.isNeedsEnrichment()).isFalse();
            });
            assertThat(stateMutationRepository.findAll()).singleElement().satisfies(mutation ->
                    assertThat(mutation.getAmount()).isEqualByComparingTo("35"));
            assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
                assertThat(session.getActiveIntent()).isNull();
                assertThat(session.getWaitingForField()).isNull();
                assertThat(session.getActiveTransactionId()).isNull();
                assertThat(session.getInterpreterVersion()).isEqualTo("unified-v1");
                assertThat(session.getPendingEvents()).isEmpty();
                assertThat(session.getRecentTurns()).isNotEmpty();
            });
        });

        sendText("wamid.expense-5", "I spent 3500 yesterday");
        awaitState(() -> {
            assertThat(stateChangeRepository.findAll()).hasSize(2);
            assertWaitingFor("category");
        });

        // This contains the verb "Paid", but is an answer to the category question,
        // not a new expense because it contains no new amount.
        sendText("wamid.expense-6", "Paid internet bill");
        awaitState(() -> {
            assertThat(stateChangeRepository.findAll())
                    .filteredOn(expense -> expense.getAmount().compareTo(new java.math.BigDecimal("3500")) == 0)
                    .singleElement()
                    .satisfies(expense -> {
                        assertThat(expense.getCategory()).isEqualTo("Utilities");
                        assertThat(expense.getSubcategory()).isEqualTo("Internet");
                    });
            assertWaitingFor("sourceAccount");
        });

        sendButton("wamid.expense-7", "answer:BANK_ACCOUNT", "Bank / UPI");
        awaitState(() -> {
            assertThat(stateContainerRepository.findAll()).singleElement().satisfies(account -> {
                assertThat(account.getCurrentValue()).isEqualByComparingTo("36465");
                assertThat(account.getAvailableValue()).isEqualByComparingTo("36465");
            });
            assertThat(stateChangeRepository.findAll()).hasSize(2)
                    .filteredOn(expense -> expense.isFinanciallyApplied())
                    .hasSize(2);
            assertThat(stateMutationRepository.findAll()).hasSize(2);
            assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
                assertThat(session.getActiveIntent()).isNull();
                assertThat(session.getWaitingForField()).isNull();
            });
        });

        // Meta may redeliver a webhook. The external message ID must prevent a duplicate expense.
        sendText("wamid.expense-1", "I spent 35");
        awaitState(() -> {
            assertThat(stateChangeRepository.findAll()).hasSize(2);
            assertThat(stateMutationRepository.findAll()).hasSize(2);
        });
    }

    private static TurnInterpretation interpretationFor(String text) {
        Map<String, Object> fields = switch (text) {
            case "I spent 35" -> Map.of("amount", 35, "transactionDate", "2026-08-06");
            case "It is for evening snacks" -> Map.of("category", "Food & Dining", "subcategory", "Snacks & Beverages");
            case "BANK_ACCOUNT" -> Map.of("sourceAccount", "BANK_ACCOUNT");
            case "40k" -> Map.of("sourceBalance", 40000);
            case "I spent 3500 yesterday" -> Map.of("amount", 3500, "transactionDate", "2026-08-05");
            case "Paid internet bill" -> Map.of("category", "Utilities", "subcategory", "Internet");
            default -> throw new AssertionError("Unexpected interpreter input: " + text);
        };
        // Reproduce the real-model mistake: a category answer containing "Paid" is labelled NEW_EVENT.
        // The deterministic correlation policy must still attach it to the pending expense.
        boolean newEvent = text.startsWith("I spent") || text.equals("Paid internet bill");
        return new TurnInterpretation(newEvent ? TurnType.NEW_EVENT : TurnType.ANSWER_TO_PENDING_EVENT,
                "EXPENSE", null,
                List.of(new EventPatch(null, "EXPENSE", fields, List.of(), List.of(), List.of())),
                null, null, List.of(), 0.99);
    }

    @TestConfiguration
    static class InterpreterFixture {
        @Bean
        @Primary
        ConversationInterpreter deterministicConversationInterpreter() {
            return (text, context) -> interpretationFor(text);
        }
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
