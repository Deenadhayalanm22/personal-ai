package com.apps.deen_sa.conversation;

import com.apps.deen_sa.core.ledger.CoreEventEntity;
import com.apps.deen_sa.core.ledger.CoreEventRepository;
import com.apps.deen_sa.extension.runtime.ExtensionInstallationService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Live acceptance design for mobile-number feature isolation at the WhatsApp boundary. */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("LiveModel")
@ActiveProfiles({"test", "live-model"})
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_MODEL_TESTS", matches = "(?i)true")
class AccessControlLiveIT extends AbstractIntegrationTestProperties {
    private static final String EXPENSE_PHONE = "919876543277";
    private static final String SAREE_PHONE = "919876543266";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository users;
    @Autowired private UserFeatureFlagRepository featureFlags;
    @Autowired private ExtensionInstallationService installations;
    @Autowired private CoreEventRepository coreEvents;

    @Value("${wiremock.admin-url}") private String wireMockAdminUrl;

    @Test
    void it_ac_live_001() throws Exception {
        requireRealApiKey();

        Long expenseTenant = createUser(EXPENSE_PHONE);
        Long sareeTenant = createUser(SAREE_PHONE);
        enableFeature(EXPENSE_PHONE, UserFeatureFlagService.EXPENSE);
        enableFeature(SAREE_PHONE, UserFeatureFlagService.SAREE_JOB_WORK);

        installations.install(expenseTenant, "personal-finance", "1.0.0", Map.of());
        installations.disable(expenseTenant, "saree-job-work", "1.0.0");
        installations.disable(sareeTenant, "personal-finance", "1.0.0");
        installations.install(sareeTenant, "saree-job-work", "1.0.0", Map.of("wageRate", 100));

        scenario("Expense-only user can record an expense");
        String expenseReply = chat(EXPENSE_PHONE, "wamid.ac-expense-allowed",
                "Today I spent 500 rupees cash on groceries");
        assertThat(expenseReply)
                .containsIgnoringCase("created Cash")
                .containsIgnoringCase("current balance");
        String expenseCompletionReply = chat(EXPENSE_PHONE, "wamid.ac-expense-balance",
                "I currently have 10,000 rupees in cash");
        assertThat(expenseCompletionReply).contains("500");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(stateChangeRepository.findAll()).anySatisfy(change -> {
                    assertThat(change.getUserId()).isEqualTo(expenseTenant.toString());
                    assertThat(change.getAmount()).isEqualByComparingTo("500");
                }));

        scenario("Expense-only user cannot use saree job work");
        String blockedSareeReply = chat(EXPENSE_PHONE, "wamid.ac-saree-denied",
                "Today I handed Selvi 1,000 metres of thread for her next lot");
        assertThat(blockedSareeReply).isNotBlank();
        assertThat(eventsFor(expenseTenant, "saree-job-work")).isEmpty();

        scenario("Saree-only user can record saree job work");
        String sareeReply = chat(SAREE_PHONE, "wamid.ac-saree-allowed",
                "Today I handed Selvi 1,000 metres of thread for her next lot");
        assertThat(sareeReply).contains("1,000 m").contains("Selvi").contains("SW-101");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(eventsFor(sareeTenant, "saree-job-work"))
                        .singleElement()
                        .extracting(CoreEventEntity::getEventType)
                        .isEqualTo("SAREE_MATERIAL_ISSUED"));

        scenario("Saree-only user cannot record an expense");
        long expenseCountBefore = expensesFor(sareeTenant);
        String blockedExpenseReply = chat(SAREE_PHONE, "wamid.ac-expense-denied",
                "Today I spent 700 rupees cash on fuel");
        assertThat(blockedExpenseReply).isNotBlank();
        assertThat(expensesFor(sareeTenant)).isEqualTo(expenseCountBefore);

        scenario("Final tenant isolation reconciliation");
        assertThat(eventsFor(expenseTenant, "saree-job-work")).isEmpty();
        assertThat(expensesFor(sareeTenant)).isZero();
        assertThat(eventsFor(sareeTenant, "saree-job-work")).hasSize(1);
        assertThat(expensesFor(expenseTenant)).isEqualTo(1);
    }

    private Long createUser(String phone) {
        AppUserEntity user = new AppUserEntity();
        user.setChannel("WHATSAPP");
        user.setExternalUserId(phone);
        return users.saveAndFlush(user).getId();
    }

    private void enableFeature(String phone, String featureKey) {
        UserFeatureFlagEntity flag = new UserFeatureFlagEntity();
        flag.setChannel("WHATSAPP");
        flag.setExternalUserId(phone);
        flag.setFeatureKey(featureKey);
        flag.setEnabled(true);
        featureFlags.saveAndFlush(flag);
    }

    private List<CoreEventEntity> eventsFor(Long tenantId, String extensionId) {
        return coreEvents.findAll().stream()
                .filter(event -> tenantId.equals(event.getTenantId()) && extensionId.equals(event.getExtensionId()))
                .toList();
    }

    private long expensesFor(Long tenantId) {
        return stateChangeRepository.findAll().stream()
                .filter(change -> tenantId.toString().equals(change.getUserId()))
                .count();
    }

    private String chat(String phone, String messageId, String text) throws Exception {
        int before = outgoingMessages(phone).size();
        mockMvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "id":"%s","from":"%s","type":"text","text":{"body":"%s"}
                }]}}]}]}
                """.formatted(messageId, phone, escape(text)))).andExpect(status().isOk());

        String[] reply = new String[1];
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(300)).untilAsserted(() -> {
            List<OutgoingMessage> messages = outgoingMessages(phone);
            assertThat(messages).hasSizeGreaterThan(before);
            reply[0] = messages.getLast().text();
            assertThat(reply[0]).isNotBlank();
        });
        return reply[0];
    }

    private List<OutgoingMessage> outgoingMessages(String phone) {
        try {
            JsonNode root = objectMapper.readTree(RestClient.create(wireMockAdminUrl).get().uri("/requests")
                    .retrieve().body(String.class));
            List<OutgoingMessage> messages = new ArrayList<>();
            for (JsonNode entry : root.path("requests")) {
                JsonNode request = entry.path("request");
                if (!request.path("url").asText().endsWith("/messages")) continue;
                JsonNode payload = objectMapper.readTree(request.path("body").asText());
                if (!phone.equals(payload.path("to").asText())) continue;
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

    private void requireRealApiKey() {
        assertThat(System.getenv("OPENAI_API_KEY"))
                .as("Set OPENAI_API_KEY before running live-model tests")
                .isNotBlank()
                .isNotEqualTo("test-api-key");
    }

    private void scenario(String title) {
        System.out.println("\n---------------- " + title + " ----------------");
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record OutgoingMessage(long loggedAt, String text) { }
}
