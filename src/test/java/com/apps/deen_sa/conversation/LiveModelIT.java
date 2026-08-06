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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(stateChangeRepository.findAll()).singleElement().satisfies(expense -> {
                assertThat(expense.getAmount()).isEqualByComparingTo("500");
                assertThat(expense.getCategory()).isNotBlank();
                assertThat((expense.getCategory() + " " + expense.getSubcategory()).toLowerCase())
                        .contains("grocer");
                assertThat(expense.isFinanciallyApplied()).isTrue();
            });
            assertThat(stateContainerRepository.findAll()).singleElement().satisfies(account -> {
                assertThat(account.getContainerType()).isEqualTo("BANK_ACCOUNT");
                assertThat(account.getCurrentValue()).isEqualByComparingTo("9500");
            });
            assertThat(stateMutationRepository.findAll()).singleElement()
                    .satisfies(mutation -> assertThat(mutation.getAmount()).isEqualByComparingTo("500"));
        });

        System.out.println("==================================================================\n");
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
