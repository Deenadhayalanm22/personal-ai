package com.apps.deen_sa.conversation;

import com.apps.deen_sa.core.ledger.CoreEventEntity;
import com.apps.deen_sa.core.ledger.CoreEventRepository;
import com.apps.deen_sa.core.ledger.CoreMovementEntity;
import com.apps.deen_sa.core.ledger.CoreMovementRepository;
import com.apps.deen_sa.extension.runtime.ExtensionInstallationService;
import com.apps.deen_sa.integration.AbstractIntegrationTestProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("LiveModel")
@ActiveProfiles({"test", "live-model"})
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_MODEL_TESTS", matches = "(?i)true")
class SareeLiveIT extends AbstractIntegrationTestProperties {
    private static final String PHONE = "919876543288";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ExtensionInstallationService installations;
    @Autowired private CoreEventRepository eventRepository;
    @Autowired private CoreMovementRepository movementRepository;

    @Value("${wiremock.admin-url}") private String wireMockAdminUrl;
    @Value("${openai.model}") private String modelName;

    @Test
    void it_sw_live_001() throws Exception {
        requireRealApiKey();
        System.out.println("\n================ SAREE MVP WHATSAPP CONVERSATION ================");
        System.out.println("Model: " + modelName);

        Long tenantId = createTenant();
        installations.install(tenantId, "saree-job-work", "1.0.0", java.util.Map.of("wageRate", 100));
        assertThat(sareeEvents(tenantId)).as("Selvi starts with no open batch").isEmpty();

        scenario("Assign thread and open Selvi's only batch");
        String issueMessageId = id(1);
        String issueText = "Today I handed Selvi 1,000 metres of thread for her next lot.";
        assertThat(chatText(issueMessageId, issueText))
                .contains("1,000 m").contains("Selvi").contains("SW-101").containsIgnoringCase("open");

        scenario("Surrender automatically accepts production and earns wages");
        String surrenderMessageId = id(2);
        String surrenderText = "Selvi brought back 24 completed sarees from that lot.";
        assertThat(chatText(surrenderMessageId, surrenderText))
                .contains("24 sarees").contains("SW-101").contains("₹2,400").contains("₹100 each")
                .containsIgnoringCase("approve");

        scenario("Approve the wage statement");
        String approvalMessageId = id(3);
        assertThat(chatText(approvalMessageId, "Yes."))
                .containsIgnoringCase("approved").contains("₹2,400").contains("Selvi")
                .containsIgnoringCase("cash or bank");

        scenario("Record cash payment against the approved statement");
        String paymentMessageId = id(4);
        String paymentText = "₹2,400 in cash, today.";
        assertThat(chatText(paymentMessageId, paymentText))
                .containsIgnoringCase("payment recorded").contains("₹2,400 cash").contains("Selvi")
                .contains("Earned ₹2,400").contains("paid ₹2,400").contains("balance ₹0")
                .contains("SW-101").containsIgnoringCase("still open");

        scenario("Persisted reconciliation");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertMvpLedger(tenantId));

        scenario("WhatsApp retries do not duplicate any business event or movement");
        sendText(issueMessageId, issueText);
        sendText(surrenderMessageId, surrenderText);
        sendText(approvalMessageId, "Yes.");
        sendText(paymentMessageId, paymentText);
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertMvpLedger(tenantId));

        System.out.println("Reconciliation: PASS — batch, auto-acceptance, wage approval, cash payment, traceability, and retries match the oracle.");
        System.out.println("==================================================================\n");
    }

    private void requireRealApiKey() {
        String key = System.getenv("OPENAI_API_KEY");
        assertThat(key)
                .as("Set OPENAI_API_KEY before running live-model tests")
                .isNotBlank()
                .isNotEqualTo("test-api-key");
    }

    private void assertMvpLedger(Long tenantId) {
        List<CoreEventEntity> events = sareeEvents(tenantId);
        assertThat(events).hasSize(4);
        assertThat(events).extracting(CoreEventEntity::getEventType).containsExactlyInAnyOrder(
                "SAREE_MATERIAL_ISSUED", "SAREE_PRODUCTION_SURRENDERED",
                "SAREE_WAGE_STATEMENT_APPROVED", "SAREE_WAGE_PAID");
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getStatus()).isEqualTo("COMMITTED");
            assertThat(event.getIdempotencyKey()).isNotBlank();
            assertThat(event.getFacts()).containsKey("rawText");
        });

        CoreEventEntity batch = events.stream()
                .filter(event -> "SAREE_MATERIAL_ISSUED".equals(event.getEventType()))
                .findFirst().orElseThrow();
        assertThat(batch.getFacts()).containsEntry("employee", "Selvi")
                .containsEntry("batchId", "SW-101").containsEntry("batchStatus", "OPEN")
                .containsEntry("unit", "m");
        assertDecimalFact(batch, "quantity", "1000");

        CoreEventEntity surrender = event(events, "SAREE_PRODUCTION_SURRENDERED");
        assertThat(surrender.getFacts()).containsEntry("employee", "Selvi").containsEntry("batchId", "SW-101")
                .containsEntry("acceptanceMode", "AUTOMATIC_MVP");
        assertDecimalFact(surrender, "quantity", "24");
        assertDecimalFact(surrender, "acceptedQuantity", "24");
        assertDecimalFact(surrender, "wageRate", "100");
        assertDecimalFact(surrender, "earnedWage", "2400");

        CoreEventEntity approval = event(events, "SAREE_WAGE_STATEMENT_APPROVED");
        assertThat(approval.getFacts()).containsEntry("employee", "Selvi").containsEntry("batchId", "SW-101");
        assertDecimalFact(approval, "approvedWage", "2400");

        CoreEventEntity payment = event(events, "SAREE_WAGE_PAID");
        assertThat(payment.getFacts()).containsEntry("employee", "Selvi").containsEntry("batchId", "SW-101")
                .containsEntry("unit", "INR").containsEntry("paymentMethod", "CASH");
        assertDecimalFact(payment, "quantity", "2400");

        List<CoreMovementEntity> movements = movementRepository.findAll().stream()
                .filter(movement -> events.stream().anyMatch(event -> event.getId().equals(movement.getEventId())))
                .toList();
        assertThat(movements).hasSize(12).allSatisfy(movement ->
                assertThat(events).anyMatch(event -> event.getId().equals(movement.getEventId())));

        assertBalance(movements, "thread-assignment", "batch:SW-101:assigned", "m", "1000");
        assertBalance(movements, "saree", "batch:SW-101:accepted", "piece", "24");
        assertBalance(movements, "inr-payable", "employee:selvi:earned", "INR", "2400");
        assertBalance(movements, "inr-payable", "employee:selvi:approved", "INR", "0");
        assertBalance(movements, "inr", "employee:selvi:received", "INR", "2400");

        assertThat(movements).noneSatisfy(movement -> assertThat(movement.getContainerId())
                .isIn("raw-material-stock", "inspection-queue", "finished-goods"));

        BigDecimal earned = movementsFor(events, movements, "SAREE_PRODUCTION_SURRENDERED", "inr-payable",
                "employee:selvi:earned");
        BigDecimal approved = movementsFor(events, movements, "SAREE_WAGE_STATEMENT_APPROVED", "inr-payable",
                "employee:selvi:approved");
        BigDecimal paid = movementsFor(events, movements, "SAREE_WAGE_PAID", "inr",
                "employee:selvi:received");
        assertThat(earned).isEqualByComparingTo("2400");
        assertThat(approved).isEqualByComparingTo("2400");
        assertThat(paid).isEqualByComparingTo("2400");
    }

    private List<CoreEventEntity> sareeEvents(Long tenantId) {
        return eventRepository.findAll().stream()
                .filter(event -> tenantId.equals(event.getTenantId()) && "saree-job-work".equals(event.getExtensionId()))
                .toList();
    }

    private CoreEventEntity event(List<CoreEventEntity> events, String type) {
        return events.stream().filter(event -> type.equals(event.getEventType())).findFirst().orElseThrow();
    }

    private void assertDecimalFact(CoreEventEntity event, String field, String expected) {
        assertThat(new BigDecimal(String.valueOf(event.getFacts().get(field)))).isEqualByComparingTo(expected);
    }

    private BigDecimal movementsFor(List<CoreEventEntity> events, List<CoreMovementEntity> movements,
                                    String eventType, String resource, String container) {
        var eventIds = events.stream().filter(event -> eventType.equals(event.getEventType()))
                .map(CoreEventEntity::getId).toList();
        return movements.stream().filter(value -> eventIds.contains(value.getEventId()))
                .filter(value -> resource.equals(value.getResourceId()) && container.equals(value.getContainerId()))
                .map(CoreMovementEntity::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void assertBalance(List<CoreMovementEntity> movements, String resource, String container,
                               String unit, String expected) {
        BigDecimal balance = movements.stream()
                .filter(value -> resource.equals(value.getResourceId())
                        && container.equals(value.getContainerId()) && unit.equals(value.getUnitId()))
                .map(CoreMovementEntity::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(balance).isEqualByComparingTo(expected);
    }

    private Long createTenant() {
        AppUserEntity user = new AppUserEntity();
        user.setChannel("WHATSAPP");
        user.setExternalUserId(PHONE);
        return userRepository.saveAndFlush(user).getId();
    }

    private String id(int sequence) { return "wamid.saree-live-" + sequence; }
    private void scenario(String title) { System.out.println("\n---------------- " + title + " ----------------"); }

    private String chatText(String messageId, String userText) throws Exception {
        int before = outgoingMessages().size();
        System.out.println("Owner: " + userText);
        sendText(messageId, userText);
        String reply = awaitNextReply(before);
        System.out.println("App: " + reply.replace("\n", "\n     "));
        return reply;
    }

    private void sendText(String messageId, String text) throws Exception {
        mockMvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "id":"%s","from":"%s","type":"text","text":{"body":"%s"}
                }]}}]}]}
                """.formatted(messageId, PHONE, escape(text)))).andExpect(status().isOk());
    }

    private String awaitNextReply(int previousCount) {
        String[] reply = new String[1];
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(300)).untilAsserted(() -> {
            List<OutgoingMessage> messages = outgoingMessages();
            assertThat(messages).hasSizeGreaterThan(previousCount);
            reply[0] = messages.getLast().text();
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
                String text = payload.path("text").path("body").asText();
                messages.add(new OutgoingMessage(request.path("loggedDate").asLong(), text));
            }
            messages.sort(Comparator.comparingLong(OutgoingMessage::loggedAt));
            return messages;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read outgoing WhatsApp requests", exception);
        }
    }

    private String escape(String text) { return text.replace("\\", "\\\\").replace("\"", "\\\""); }
    private record OutgoingMessage(long loggedAt, String text) { }
}
