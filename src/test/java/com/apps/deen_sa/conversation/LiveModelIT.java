package com.apps.deen_sa.conversation;

import com.apps.deen_sa.integration.AbstractIntegrationTestProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("LiveModel")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "live-model"})
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_MODEL_TESTS", matches = "(?i)true")
class LiveModelIT extends AbstractIntegrationTestProperties {
    private static final String PHONE = "919876543299";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ConversationSessionRepository sessionRepository;

    @Value("${wiremock.admin-url}") private String wireMockAdminUrl;
    @Value("${openai.model}") private String modelName;

    @Test
    void it_live_001() throws Exception {
        requireRealApiKey();
        System.out.println("\n================ LIVE MODEL WHATSAPP CONVERSATION ================");
        System.out.println("Model: " + modelName);

        String introduction = chatText("wamid.live-1", "Hi");
        assertThat(introduction).containsIgnoringCase("record").contains("I spent 500");

        String paymentQuestion = chatText("wamid.live-2", "I spent 500 on groceries");
        assertThat(paymentQuestion).containsIgnoringCase("pay");
        assertWaitingFor("sourceAccount");

        String balanceQuestion = chatButton("wamid.live-3", "answer:BANK_ACCOUNT", "Bank / UPI");
        assertThat(balanceQuestion).containsIgnoringCase("current balance");
        assertWaitingFor("sourceBalance");

        String confirmation = chatText("wamid.live-4", "10k");
        assertThat(confirmation).contains("₹500").contains("₹9500");

        String secondConfirmation = chatText(
                "wamid.live-5",
                "I spent 58 on curd and some ice cream through UPI");
        assertThat(secondConfirmation)
                .as("Explicit UPI must be extracted as sourceAccount=BANK_ACCOUNT; the app must not ask payment again")
                .contains("₹58")
                .doesNotContainIgnoringCase("how did you pay");

        String todaySummary = chatText("wamid.live-6", "What did I spend today?");
        assertThat(todaySummary)
                .contains("558")
                .containsIgnoringCase("today");

        scenario("English · complete sentence");
        int beforeEnglishComplete = expenseCount();
        String englishComplete = chatText("wamid.live-7", "Paid BESCOM electricity bill of 1850 using UPI");
        assertThat(englishComplete).contains("₹1850");
        assertExpenseApplied(beforeEnglishComplete + 1, "1850");

        scenario("Tamil · complete sentence");
        int beforeTamilComplete = expenseCount();
        String tamilComplete = chatText("wamid.live-8", "இன்று மளிகை பொருட்களுக்கு 230 ரூபாய் UPI மூலம் செலவு செய்தேன்");
        assertThat(tamilComplete).contains("₹230");
        assertExpenseApplied(beforeTamilComplete + 1, "230");

        scenario("Tanglish · complete sentence");
        int beforeTanglishComplete = expenseCount();
        String tanglishComplete = chatText("wamid.live-9", "Inniku bike petrol ku 350 rupees UPI la spend pannen");
        assertThat(tanglishComplete).contains("₹350");
        assertExpenseApplied(beforeTanglishComplete + 1, "350");

        scenario("English · sparse expense with category and payment follow-ups");
        int beforeSparse = expenseCount();
        String categoryQuestion = chatText("wamid.live-10", "I spent 260");
        assertThat(categoryQuestion).contains("₹260").containsIgnoringCase("expense for");
        assertWaitingFor("category");
        assertExpenseCount(beforeSparse + 1);

        String sparsePaymentQuestion = chatText("wamid.live-11", "Coffee and snacks outside");
        assertThat(sparsePaymentQuestion).containsIgnoringCase("pay");
        assertWaitingFor("sourceAccount");

        String sparseConfirmation = chatButton("wamid.live-12", "answer:BANK_ACCOUNT", "Bank / UPI");
        assertThat(sparseConfirmation).contains("₹260");
        assertExpenseApplied(beforeSparse + 1, "260");

        scenario("Tamil · understood expense with missing payment source");
        int beforeTamilPartial = expenseCount();
        String tamilPaymentQuestion = chatText("wamid.live-13", "நேற்று மின்சார கட்டணத்திற்கு 650 ரூபாய் செலவு செய்தேன்");
        assertThat(tamilPaymentQuestion).containsIgnoringCase("pay");
        assertWaitingFor("sourceAccount");
        String tamilConfirmation = chatButton("wamid.live-14", "answer:BANK_ACCOUNT", "Bank / UPI");
        assertThat(tamilConfirmation).contains("₹650");
        assertExpenseApplied(beforeTamilPartial + 1, "650");

        scenario("Tanglish · missing amount, followed by amount and payment source");
        int beforeTanglishPartial = expenseCount();
        String amountQuestion = chatText("wamid.live-15", "Nethu office lunch ku spend pannen");
        assertThat(amountQuestion).containsIgnoringCase("how much");
        assertWaitingFor("amount");
        assertExpenseCount(beforeTanglishPartial);

        String tanglishSourceQuestion = chatText("wamid.live-16", "450");
        assertThat(tanglishSourceQuestion).containsIgnoringCase("pay");
        assertWaitingFor("sourceAccount");
        assertExpenseCount(beforeTanglishPartial + 1);

        String tanglishPartialConfirmation = chatButton("wamid.live-17", "answer:BANK_ACCOUNT", "Bank / UPI");
        assertThat(tanglishPartialConfirmation).contains("₹450");
        assertExpenseApplied(beforeTanglishPartial + 1, "450");

        scenario("English · two expenses in one natural sentence");
        int beforeMultiple = expenseCount();
        String multipleConfirmation = chatText("wamid.live-18", "Spent 80 on tea and 120 on auto using UPI");
        assertThat(multipleConfirmation).contains("80").contains("120");
        assertExpenseApplied(beforeMultiple + 2, "80");
        assertExpenseApplied(beforeMultiple + 2, "120");

        scenario("English, Tamil, and Tanglish · read-only queries must not create expenses");
        int beforeQueries = expenseCount();
        String englishQuery = chatText("wamid.live-19", "What did I spend today?");
        assertThat(englishQuery).contains("₹");
        assertExpenseCount(beforeQueries);

        String tamilQuery = chatText("wamid.live-20", "இன்று நான் எவ்வளவு செலவு செய்தேன்?");
        assertThat(tamilQuery).contains("₹");
        assertExpenseCount(beforeQueries);

        String tanglishQuery = chatText("wamid.live-21", "Inniku naan evlo spend pannen?");
        assertThat(tanglishQuery).contains("₹");
        assertExpenseCount(beforeQueries);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(stateChangeRepository.findAll()).hasSize(10).allSatisfy(expense -> {
                assertThat(expense.getCategory()).isNotBlank();
                assertThat(expense.isFinanciallyApplied()).isTrue();
            }).anySatisfy(expense -> {
                assertThat(expense.getAmount()).isEqualByComparingTo("500");
                assertThat((expense.getCategory() + " " + expense.getSubcategory()).toLowerCase())
                        .contains("grocer");
            }).anySatisfy(expense ->
                    assertThat(expense.getAmount()).isEqualByComparingTo("58"));
            assertThat(stateContainerRepository.findAll()).singleElement().satisfies(account -> {
                assertThat(account.getContainerType()).isEqualTo("BANK_ACCOUNT");
                assertThat(account.getCurrentValue()).isEqualByComparingTo("5452");
            });
            assertThat(stateMutationRepository.findAll()).hasSize(10)
                    .anySatisfy(mutation -> assertThat(mutation.getAmount()).isEqualByComparingTo("500"))
                    .anySatisfy(mutation -> assertThat(mutation.getAmount()).isEqualByComparingTo("58"))
                    .anySatisfy(mutation -> assertThat(mutation.getAmount()).isEqualByComparingTo("1850"))
                    .anySatisfy(mutation -> assertThat(mutation.getAmount()).isEqualByComparingTo("230"))
                    .anySatisfy(mutation -> assertThat(mutation.getAmount()).isEqualByComparingTo("350"))
                    .anySatisfy(mutation -> assertThat(mutation.getAmount()).isEqualByComparingTo("260"))
                    .anySatisfy(mutation -> assertThat(mutation.getAmount()).isEqualByComparingTo("650"))
                    .anySatisfy(mutation -> assertThat(mutation.getAmount()).isEqualByComparingTo("450"))
                    .anySatisfy(mutation -> assertThat(mutation.getAmount()).isEqualByComparingTo("80"))
                    .anySatisfy(mutation -> assertThat(mutation.getAmount()).isEqualByComparingTo("120"));
        });

        System.out.println("==================================================================\n");
    }

    private void scenario(String title) {
        System.out.println("\n---------------- " + title + " ----------------");
    }

    private int expenseCount() {
        return stateChangeRepository.findAll().size();
    }

    private void assertExpenseCount(int expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(stateChangeRepository.findAll()).hasSize(expected));
    }

    private void assertExpenseApplied(int expectedCount, String amount) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(stateChangeRepository.findAll()).hasSize(expectedCount)
                    .filteredOn(expense -> expense.getAmount().compareTo(new BigDecimal(amount)) == 0)
                    .isNotEmpty()
                    .allSatisfy(expense -> {
                        assertThat(expense.isFinanciallyApplied()).isTrue();
                        assertThat(expense.getSourceContainerId()).isNotNull();
                    });
        });
    }

    private void requireRealApiKey() {
        String key = System.getenv("OPENAI_API_KEY");
        assertThat(key)
                .as("Set OPENAI_API_KEY before running live-model tests")
                .isNotBlank()
                .isNotEqualTo("test-api-key");
    }

    private void assertWaitingFor(String field) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
                    assertThat(session.getActiveIntent()).isEqualTo("EXPENSE");
                    assertThat(session.getWaitingForField()).isEqualTo(field);
                    assertThat(session.getLastQuestion()).isNotBlank();
                }));
    }

    private String chatText(String messageId, String userText) throws Exception {
        int before = outgoingMessages().size();
        System.out.println("Deena: " + userText);
        sendText(messageId, userText);
        return printReply(awaitNextReply(before));
    }

    private String chatButton(String messageId, String buttonId, String title) throws Exception {
        int before = outgoingMessages().size();
        System.out.println("Deena: " + title + "  [button]");
        sendButton(messageId, buttonId, title);
        return printReply(awaitNextReply(before));
    }

    private String printReply(String reply) {
        System.out.println("App: " + reply.replace("\n", "\n     "));
        return reply;
    }

    private String awaitNextReply(int previousCount) {
        String[] reply = new String[1];
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(300)).untilAsserted(() -> {
            List<OutgoingMessage> messages = outgoingMessages();
            assertThat(messages).hasSizeGreaterThan(previousCount);
            reply[0] = messages.getLast().text();
            assertThat(reply[0]).isNotBlank();
        });
        return reply[0];
    }

    private void sendText(String messageId, String text) throws Exception {
        postWebhook("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "id":"%s","from":"%s","type":"text","text":{"body":"%s"}
                }]}}]}]}
                """.formatted(messageId, PHONE, escape(text)));
    }

    private void sendButton(String messageId, String buttonId, String title) throws Exception {
        postWebhook("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "id":"%s","from":"%s","type":"interactive",
                  "interactive":{"type":"button_reply","button_reply":{"id":"%s","title":"%s"}}
                }]}}]}]}
                """.formatted(messageId, PHONE, escape(buttonId), escape(title)));
    }

    private void postWebhook(String payload) throws Exception {
        mockMvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<OutgoingMessage> outgoingMessages() {
        try {
            JsonNode root = objectMapper.readTree(RestClient.create(wireMockAdminUrl).get().uri("/requests")
                    .retrieve().body(String.class));
            List<OutgoingMessage> messages = new ArrayList<>();
            for (JsonNode entry : root.path("requests")) {
                JsonNode request = entry.path("request");
                if (!request.path("url").asText().endsWith("/messages")) continue;
                JsonNode payload = objectMapper.readTree(request.path("body").asText());
                String text = "interactive".equals(payload.path("type").asText())
                        ? payload.path("interactive").path("body").path("text").asText()
                        : payload.path("text").path("body").asText();
                messages.add(new OutgoingMessage(request.path("loggedDate").asLong(), text));
            }
            messages.sort(Comparator.comparingLong(OutgoingMessage::loggedAt));
            return messages;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read outgoing WhatsApp requests", exception);
        }
    }

    private record OutgoingMessage(long loggedAt, String text) { }
}
