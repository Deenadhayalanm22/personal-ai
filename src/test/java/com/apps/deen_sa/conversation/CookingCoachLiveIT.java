package com.apps.deen_sa.conversation;

import com.apps.deen_sa.cooking.session.CookingSessionRepository;
import com.apps.deen_sa.cooking.session.CookingSessionStatus;
import com.apps.deen_sa.integration.PostgresTestPropertiesInitializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
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

/**
 * Live WhatsApp conversation for the complete chicken-biryani MVP.
 *
 * Start PostgreSQL and WireMock with src/test/resources/infra/podman-compose.yml, then run:
 * RUN_LIVE_MODEL_TESTS=true OPENAI_API_KEY=... ./mvnw verify -Pintegration \
 *   -Dit.test=CookingCoachLiveIT
 */
@Tag("LiveModel")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "live-model"})
@ContextConfiguration(initializers = PostgresTestPropertiesInitializer.class)
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_MODEL_TESTS", matches = "(?i)true")
class CookingCoachLiveIT {
    private static final String PHONE = "919876543299";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Flyway flyway;
    @Autowired private UserFeatureFlagRepository accessRepository;
    @Autowired private CookingSessionRepository sessionRepository;
    @Autowired private InboundMessageRepository inboundRepository;
    @Value("${wiremock.admin-url}") private String wireMockAdminUrl;

    @BeforeEach
    void reset() {
        requireRealApiKey();
        flyway.clean();
        flyway.migrate();
        UserFeatureFlagEntity access = new UserFeatureFlagEntity();
        access.setChannel("WHATSAPP");
        access.setExternalUserId(PHONE);
        access.setRole(UserFeatureFlagService.USER);
        access.setEnabled(true);
        accessRepository.saveAndFlush(access);
        RestClient.create(wireMockAdminUrl).delete().uri("/requests").retrieve().toBodilessEntity();
    }

    @Test
    void it_live_coaches_a_complete_biryani_and_recovers_from_a_mistake() throws Exception {
        int message = 1;

        scenario("Collect the validated recipe setup one decision at a time");
        assertThat(chat(id(message++), "Start chicken biryani"))
                .contains("Are you ready to prepare the chicken biryani?").contains("Yes or No");
        assertThat(sessionRepository.findAll()).isEmpty();

        assertThat(chat(id(message++), "Yes")).contains("How much chicken do you have?");
        assertThat(chat(id(message++), "600g")).contains("Which rice are you using?")
                .contains("Basmati").contains("Seeraga Samba").contains("Other");
        assertThat(chat(id(message++), "Basmati")).contains("For 600 g chicken")
                .contains("recommends 500 g rice").contains("Do you want to alter");
        assertThat(chat(id(message++), "No")).contains("What are you cooking in?")
                .contains("Pressure cooker").contains("Biryani pot");

        String start = chat(id(message++), "Biryani pot");
        assertThat(start).contains("Setup complete").contains("500 g Basmati rice")
                .contains("600 g chicken").contains("biryani pot")
                .containsIgnoringCase("long-grain basmati rice: 500 g")
                .contains("Chicken, bone-in curry cut: 600 g").contains("Prepare everything");
        assertSession(CookingSessionStatus.PREPARING, 0);

        scenario("Begin the configured recipe and prove duplicate webhooks cannot skip a step");
        assertThat(chat(id(message++), "ready")).contains("Step 1/8").contains("Measure, soak and prepare")
                .contains("Do not parboil the rice yet");
        String doneId = id(message++);
        assertThat(chat(doneId, "done")).contains("Step 2/8").contains("Fry whole spices");
        int repliesBeforeDuplicate = outgoingMessages().size();
        sendText(doneId, "done");
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertSession(CookingSessionStatus.COOKING, 1);
            assertThat(outgoingMessages()).hasSize(repliesBeforeDuplicate);
            assertThat(inboundRepository.findAll()).filteredOn(row -> doneId.equals(row.getExternalMessageId())).hasSize(1);
        });

        scenario("Reach the onion stage, pause, and resume without losing progress");
        assertThat(chat(id(message++), "done")).contains("Step 3/8").contains("onion-tomato gravy");
        assertThat(chat(id(message++), "pause")).contains("Paused at step 3");
        assertSession(CookingSessionStatus.PAUSED, 2);
        assertThat(chat(id(message++), "resume")).contains("Resumed").contains("Step 3/8");

        scenario("Recover deterministically when the gravy catches");
        String recovery = chat(id(message++), "The masala is sticking at the bottom. What should I do?");
        assertThat(recovery).contains("Act now").containsIgnoringCase("clean pot")
                .containsIgnoringCase("without scraping").contains("remembered this adjustment");
        assertThat(activeSession().getAdjustmentNotes()).contains("Onion-tomato base catching");

        scenario("Coordinate hot rice with boiling chicken gravy without simultaneous hands-on work");
        assertThat(chat(id(message++), "done")).contains("Step 4/8").contains("Season and cook curd");
        String coordinated = chat(id(message++), "done");
        assertThat(coordinated)
                .contains("Step 5/8")
                .contains("Coordinate chicken gravy and half-cooked rice")
                .contains("Coordinate in parallel (one action at a time)")
                .contains("Second pot — parboil rice")
                .contains("Immediately after adding chicken")
                .contains("rolling boil")
                .contains("roughly 5–7 minutes from readiness")
                .contains("Drain immediately")
                .contains("brief 1–2 minute wait")
                .contains("Advance only when the primary step and every parallel task are ready");
        assertSession(CookingSessionStatus.COOKING, 4);

        scenario("Block progression until the gravy and rice readiness gates are both satisfied");
        String blocked = chat(id(message++), "done");
        assertThat(blocked).contains("Before advancing").contains("Second pot — parboil rice")
                .contains("both ready").contains("early or delayed");
        assertSession(CookingSessionStatus.COOKING, 4);

        assertThat(chat(id(message++), "both ready")).contains("Step 6/8").contains("Place rice and remaining water");
        assertSession(CookingSessionStatus.COOKING, 5);

        scenario("Ask a genuine model-grounded question after synchronized placement");
        String explanation = chat(id(message++), "Why should I reposition the rice but not mix it into the gravy?");
        assertThat(explanation).isNotBlank().containsAnyOf("layer", "Layer", "rice", "Rice", "masala");
        assertSession(CookingSessionStatus.COOKING, 5);

        scenario("Finish dum, rest, and complete the persisted session");
        assertThat(chat(id(message++), "done")).contains("Step 7/8").contains("Dum cook");
        assertThat(chat(id(message++), "progress")).contains("step 7 of 8");
        assertThat(chat(id(message++), "done")).contains("Step 8/8").contains("Rest, verify and combine");
        assertThat(chat(id(message), "done")).contains("Biryani complete").contains("10-minute")
                .contains("74°C/165°F");
        assertSession(CookingSessionStatus.COMPLETED, 7);
    }

    private void requireRealApiKey() {
        assertThat(System.getenv("OPENAI_API_KEY"))
                .as("Set OPENAI_API_KEY before running live-model tests")
                .isNotBlank().isNotEqualTo("test-api-key");
    }

    private String chat(String messageId, String userText) throws Exception {
        int before = outgoingMessages().size();
        System.out.println("Cook: " + userText);
        sendText(messageId, userText);
        String reply = awaitNextReply(before);
        System.out.println("Coach: " + reply.replace("\n", "\n       "));
        return reply;
    }

    private void sendText(String messageId, String text) throws Exception {
        String body = objectMapper.writeValueAsString(text);
        postWebhook("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "id":"%s","from":"%s","type":"text","text":{"body":%s}
                }]}}]}]}
                """.formatted(messageId, PHONE, body));
    }

    private void postWebhook(String payload) throws Exception {
        mockMvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
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

    private List<OutgoingMessage> outgoingMessages() {
        try {
            JsonNode root = objectMapper.readTree(RestClient.create(wireMockAdminUrl).get().uri("/requests")
                    .retrieve().body(String.class));
            List<OutgoingMessage> messages = new ArrayList<>();
            for (JsonNode entry : root.path("requests")) {
                JsonNode request = entry.path("request");
                if (!request.path("url").asText().endsWith("/messages")) continue;
                JsonNode payload = objectMapper.readTree(request.path("body").asText());
                messages.add(new OutgoingMessage(request.path("loggedDate").asLong(),
                        payload.path("text").path("body").asText()));
            }
            messages.sort(Comparator.comparingLong(OutgoingMessage::loggedAt));
            return messages;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read outgoing WhatsApp requests", exception);
        }
    }

    private void assertSession(CookingSessionStatus status, int step) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(activeSession().getStatus()).isEqualTo(status);
            assertThat(activeSession().getCurrentStep()).isEqualTo(step);
        });
    }

    private com.apps.deen_sa.cooking.session.CookingSessionEntity activeSession() {
        return sessionRepository.findAll().stream().max(Comparator.comparingLong(value -> value.getId())).orElseThrow();
    }

    private void scenario(String title) { System.out.println("\n---------------- " + title + " ----------------"); }
    private String id(int value) { return "wamid.biryani-live-" + value; }
    private record OutgoingMessage(long loggedAt, String text) { }
}
