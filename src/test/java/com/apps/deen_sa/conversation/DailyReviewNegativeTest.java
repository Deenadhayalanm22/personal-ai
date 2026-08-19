package com.apps.deen_sa.conversation;

import com.apps.deen_sa.finance.legacy.state.CompletenessLevelEnum;
import com.apps.deen_sa.integration.AbstractIntegrationTestProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.apps.deen_sa.conversation.interpretation.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("Layer1")
@SpringBootTest
@AutoConfigureMockMvc
@Import(DailyReviewNegativeTest.InterpreterFixture.class)
class DailyReviewNegativeTest extends AbstractIntegrationTestProperties {

    private static final String PHONE = "919876543210";
    private static final AtomicInteger INTERPRETER_CALLS = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationSessionRepository sessionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${wiremock.admin-url:http://localhost:9091/__admin}")
    private String wireMockAdminUrl;

    @BeforeEach
    void resetInterpreterCallCount() {
        INTERPRETER_CALLS.set(0);
    }

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
                assertThat(session.getInterpreterVersion()).isEqualTo("unified-v2");
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

        sendButton("wamid.expense-7", "answer:CREDIT_CARD", "Credit Card");
        awaitState(() -> {
            assertThat(stateContainerRepository.findAll()).hasSize(2)
                    .filteredOn(account -> account.getContainerType().equals("CREDIT_CARD"))
                    .singleElement().satisfies(card -> assertThat(card.getCurrentValue()).isNull());
            assertWaitingFor("creditLimit");
        });

        sendText("wamid.expense-8", "50k");
        awaitState(() -> {
            assertThat(stateContainerRepository.findAll())
                    .filteredOn(account -> account.getContainerType().equals("CREDIT_CARD"))
                    .singleElement().satisfies(card -> assertThat(card.getCapacityLimit()).isEqualByComparingTo("50000"));
            assertWaitingFor("creditCardDueDay");
        });

        sendText("wamid.expense-9", "21st");
        awaitState(() -> {
            assertThat(stateContainerRepository.findAll())
                    .filteredOn(account -> account.getContainerType().equals("CREDIT_CARD"))
                    .singleElement().satisfies(card -> assertThat(card.getDetails()).containsEntry("dueDay", 21));
            assertWaitingFor("sourceBalance");
        });

        sendText("wamid.expense-10", "0");
        awaitState(() -> {
            assertThat(stateContainerRepository.findAll())
                    .filteredOn(account -> account.getContainerType().equals("CREDIT_CARD"))
                    .singleElement().satisfies(card -> assertThat(card.getCurrentValue()).isEqualByComparingTo("3500"));
            assertThat(stateContainerRepository.findAll())
                    .filteredOn(account -> account.getContainerType().equals("BANK_ACCOUNT"))
                    .singleElement().satisfies(bank -> assertThat(bank.getCurrentValue()).isEqualByComparingTo("39965"));
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

    @Test
    void it_neg_002() throws Exception {
        System.out.println("\n================ WHATSAPP CONVERSATION ================");

        String intro = chatText("wamid.flow-1", "Hi");
        assertThat(intro)
                .contains("I can help record operational activity")
                .contains("Describe what happened naturally");

        String categoryQuestion = chatText("wamid.flow-2", "I spent 500");
        assertThat(categoryQuestion).contains("What was the ₹500 expense for?");

        String paymentQuestion = chatText("wamid.flow-3", "I bought groceries");
        assertThat(paymentQuestion).isEqualTo("How did you pay?");

        String balanceQuestion = chatButton("wamid.flow-4", "answer:BANK_ACCOUNT", "Bank / UPI");
        assertThat(balanceQuestion).contains("What is its current balance?");

        String confirmation = chatText("wamid.flow-5", "10k");
        assertThat(confirmation)
                .contains("Added ₹500 for Groceries")
                .contains("balance is now ₹9500");

        // Reproduce the production failure deterministically: the interpreter fixture deliberately
        // returns a second ₹58 expense copied from history for this read-only question.
        String summary = chatText("wamid.flow-6", "what i spent today?");
        assertThat(summary).contains("record this as a new activity");

        String safeSummary = chatText("wamid.flow-7", "Show today's spending");
        assertThat(safeSummary).contains("Total spent: ₹500", "Category breakdown");

        awaitState(() -> {
            assertThat(stateChangeRepository.findAll()).singleElement().satisfies(expense -> {
                assertThat(expense.getAmount()).isEqualByComparingTo("500");
                assertThat(expense.getCategory()).isEqualTo("Groceries");
                assertThat(expense.isFinanciallyApplied()).isTrue();
            });
            assertThat(stateContainerRepository.findAll()).singleElement()
                    .satisfies(account -> assertThat(account.getCurrentValue()).isEqualByComparingTo("9500"));
            assertThat(stateMutationRepository.findAll()).singleElement();
        });
        assertThat(INTERPRETER_CALLS).hasValue(4);

        System.out.println("=======================================================\n");
    }

    @Test
    void it_neg_003_completesExpenseThatInitiallyHasNoAmount() throws Exception {
        String amountQuestion = chatText("wamid.missing-1", "Nethu office lunch ku spend pannen");
        assertThat(amountQuestion).isEqualTo("How much did you spend?");
        assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
            assertThat(session.getActiveIntent()).isEqualTo("EXPENSE");
            assertThat(session.getWaitingForField()).isEqualTo("amount");
            assertThat(session.getActiveTransactionId()).isNull();
            assertThat(session.getLastQuestion()).isEqualTo("How much did you spend?");
        });
        assertThat(stateChangeRepository.findAll()).isEmpty();

        String sourceQuestion = chatText("wamid.missing-2", "450");
        assertThat(sourceQuestion).isEqualTo("How did you pay?");
        assertWaitingFor("sourceAccount");
        assertThat(stateChangeRepository.findAll()).singleElement()
                .satisfies(expense -> assertThat(expense.getAmount()).isEqualByComparingTo("450"));

        String balanceQuestion = chatButton("wamid.missing-3", "answer:BANK_ACCOUNT", "Bank / UPI");
        assertThat(balanceQuestion).contains("current balance");
        String confirmation = chatText("wamid.missing-4", "10k");
        assertThat(confirmation).contains("₹450").contains("₹9550");

        awaitState(() -> {
            assertThat(stateChangeRepository.findAll()).singleElement()
                    .satisfies(expense -> assertThat(expense.isFinanciallyApplied()).isTrue());
            assertThat(stateMutationRepository.findAll()).singleElement();
        });
        assertThat(INTERPRETER_CALLS).hasValue(1);
    }

    @Test
    void it_neg_004_keepsPendingQuestionWhenFollowupIsAmbiguous() throws Exception {
        String categoryQuestion = chatText("wamid.ambiguous-1", "I spent 260");
        assertThat(categoryQuestion).contains("What was the ₹260 expense for?");

        String repeatedQuestion = chatText("wamid.ambiguous-2", "???");
        assertThat(repeatedQuestion).isEqualTo(categoryQuestion);
        assertWaitingFor("category");
        assertThat(stateChangeRepository.findAll()).singleElement()
                .satisfies(expense -> assertThat(expense.getAmount()).isEqualByComparingTo("260"));
    }

    private static TurnInterpretation interpretationFor(String text) {
        Map<String, Object> fields = switch (text) {
            case "Hi" -> Map.of();
            case "I spent 500" -> Map.of("amount", 500, "transactionDate", "2026-08-06");
            case "I spent 260" -> Map.of("amount", 260, "transactionDate", "2026-08-06");
            case "I bought groceries" -> Map.of("category", "Groceries", "subcategory", "Groceries");
            case "10k" -> Map.of("sourceBalance", 10000);
            case "I spent 35" -> Map.of("amount", 35, "transactionDate", "2026-08-06");
            case "It is for evening snacks" -> Map.of("category", "Food & Dining", "subcategory", "Snacks & Beverages");
            case "BANK_ACCOUNT" -> Map.of("sourceAccount", "BANK_ACCOUNT");
            case "40k" -> Map.of("sourceBalance", 40000);
            case "50k" -> Map.of("creditLimit", 50000);
            case "21st" -> Map.of("creditCardDueDay", 21);
            // Real structured-output regression: unknown values arrived as literal "null" strings.
            case "I spent 3500 yesterday" -> Map.of("amount", 3500, "transactionDate", "2026-08-05",
                    "category", "null", "sourceAccount", "null");
            case "Paid internet bill" -> Map.of("category", "Utilities", "subcategory", "Internet");
            // Exact regression seen in production: history leaked the prior amount/category into a query.
            case "what i spent today?" -> Map.of("amount", 58, "category", "Food & Dining",
                    "subcategory", "curd and some icecream", "transactionDate", "2026-08-06");
            case "Show today's spending" -> Map.of();
            case "Nethu office lunch ku spend pannen" -> Map.of(
                    "category", "Food & Dining", "subcategory", "Office lunch");
            case "???" -> Map.of();
            default -> throw new AssertionError("Unexpected interpreter input: " + text);
        };
        // Reproduce the real-model mistake: a category answer containing "Paid" is labelled NEW_EVENT.
        // The deterministic correlation policy must still attach it to the pending expense.
        if (text.equals("Hi")) {
            return new TurnInterpretation(TurnType.AMBIGUOUS, "UNKNOWN", "en-IN", null,
                    List.of(), null, QueryPeriod.NONE, List.of("No financial activity found"), 0.2);
        }
        if (text.equals("???")) {
            return new TurnInterpretation(TurnType.AMBIGUOUS, "EXPENSE", "en-IN", null,
                    List.of(), null, QueryPeriod.NONE, List.of("Pending category was not answered"), 0.2);
        }
        if (text.equals("Show today's spending")) {
            return new TurnInterpretation(TurnType.QUERY, "QUERY", "en-IN", null,
                    List.of(), null, QueryPeriod.TODAY, List.of(), 0.99);
        }
        boolean newEvent = text.startsWith("I spent") || text.equals("Paid internet bill")
                || text.equals("what i spent today?") || text.equals("Nethu office lunch ku spend pannen");
        List<FieldEvidence> evidence = fields.containsKey("amount")
                ? List.of(new FieldEvidence("amount", fields.get("amount").toString(),
                    text.equals("what i spent today?") ? "58" : fields.get("amount").toString(), 0.99))
                : List.of();
        return new TurnInterpretation(newEvent ? TurnType.NEW_EVENT : TurnType.ANSWER_TO_PENDING_EVENT,
                "EXPENSE", "en-IN", null,
                List.of(new EventPatch(null, "EXPENSE", fields, List.of(), List.of(), evidence)),
                null, QueryPeriod.NONE, List.of(), 0.99);
    }

    @TestConfiguration
    static class InterpreterFixture {
        @Bean
        @Primary
        ConversationInterpreter deterministicConversationInterpreter() {
            return (text, context) -> {
                INTERPRETER_CALLS.incrementAndGet();
                return interpretationFor(text);
            };
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

    private String chatText(String messageId, String userText) throws Exception {
        int before = outgoingMessages().size();
        System.out.println("Deena: " + userText);
        sendText(messageId, userText);
        String reply = awaitNextReply(before);
        System.out.println("App: " + reply.replace("\n", "\n     "));
        return reply;
    }

    private String chatButton(String messageId, String buttonId, String title) throws Exception {
        int before = outgoingMessages().size();
        System.out.println("Deena: " + title + "  [button]");
        sendButton(messageId, buttonId, title);
        String reply = awaitNextReply(before);
        System.out.println("App: " + reply.replace("\n", "\n     "));
        return reply;
    }

    private String awaitNextReply(int previousCount) {
        final String[] reply = new String[1];
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            List<OutgoingMessage> messages = outgoingMessages();
            assertThat(messages).hasSizeGreaterThan(previousCount);
            reply[0] = messages.getLast().text();
            assertThat(reply[0]).isNotBlank();
        });
        return reply[0];
    }

    private List<OutgoingMessage> outgoingMessages() {
        try {
            String journal = RestClient.create(wireMockAdminUrl).get().uri("/requests")
                    .retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(journal);
            java.util.ArrayList<OutgoingMessage> messages = new java.util.ArrayList<>();
            for (JsonNode entry : root.path("requests")) {
                JsonNode request = entry.path("request");
                if (!request.path("url").asText().endsWith("/messages")) continue;
                JsonNode payload = objectMapper.readTree(request.path("body").asText());
                String text = payload.path("type").asText().equals("interactive")
                        ? payload.path("interactive").path("body").path("text").asText()
                        : payload.path("text").path("body").asText();
                messages.add(new OutgoingMessage(request.path("loggedDate").asLong(), text));
            }
            messages.sort(java.util.Comparator.comparingLong(OutgoingMessage::loggedAt));
            return messages;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read outgoing WhatsApp requests", exception);
        }
    }

    private record OutgoingMessage(long loggedAt, String text) { }

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
