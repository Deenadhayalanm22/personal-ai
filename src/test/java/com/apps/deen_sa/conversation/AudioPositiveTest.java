package com.apps.deen_sa.conversation;

import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.state.StateContainerRepository;
import com.apps.deen_sa.integration.AbstractIntegrationTestProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("Layer1")
@SpringBootTest
@AutoConfigureMockMvc
class AudioPositiveTest extends AbstractIntegrationTestProperties {

    private static final String USER_PHONE_NUMBER = "919876543210";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StateContainerRepository stateContainerRepository;

    @Autowired
    private AudioConfirmationRepository audioConfirmationRepository;

    @Test
    void it_aud_001() throws Exception {
        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "from": "%s",
                          "type": "audio",
                          "audio": {
                            "id": "test-audio-media-id",
                            "mime_type": "audio/ogg; codecs=opus"
                          }
                        }]
                      }
                    }]
                  }]
                }
                """.formatted(USER_PHONE_NUMBER);

        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        AudioConfirmationEntity confirmation = await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(250))
                .until(() -> audioConfirmationRepository.findAll().stream().findFirst(), java.util.Optional::isPresent)
                .orElseThrow();

        assertThat(confirmation.getStatus()).isEqualTo(AudioConfirmationStatus.PENDING);
        assertThat(confirmation.getWhatsappUserId()).isEqualTo(USER_PHONE_NUMBER);
        assertThat(confirmation.getMediaId()).isEqualTo("test-audio-media-id");
        assertThat(confirmation.getTranscribedText())
                .isEqualTo("Setup my hdfc bank account where i have currently 40k balance as of now");
        assertThat(confirmation.getExpiresAt()).isAfter(confirmation.getCreatedAt());
        assertThat(stateContainerRepository.findAll()).isEmpty();
        assertThat(outgoingWhatsAppMessageCount("Should I process this?")).isEqualTo(1);

        String confirmationPayload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "from": "%s",
                          "type": "interactive",
                          "interactive": {
                            "type": "button_reply",
                            "button_reply": {
                              "id": "audio_confirm:%s",
                              "title": "Yes"
                            }
                          }
                        }]
                      }
                    }]
                  }]
                }
                """.formatted(USER_PHONE_NUMBER, confirmation.getId());

        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationPayload))
                .andExpect(status().isOk());

        await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    assertThat(stateContainerRepository.findAll())
                            .hasSize(1)
                            .allSatisfy(this::assertPersistedAudioAccount);
                    assertThat(outgoingWhatsAppMessageCount("Saved successfully.")).isEqualTo(1);
                    assertThat(audioConfirmationRepository.findById(confirmation.getId()))
                            .get().extracting(AudioConfirmationEntity::getStatus)
                            .isEqualTo(AudioConfirmationStatus.COMPLETED);
                });
    }

    private int outgoingWhatsAppMessageCount(String bodyText) {
        JsonNode response = RestClient.create("http://localhost:9091/__admin")
                .post()
                .uri("/requests/count")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "method": "POST",
                          "urlPath": "/v19.0/test-phone-id/messages",
                          "bodyPatterns": [{"contains": "%s"}]
                        }
                        """.formatted(bodyText))
                .retrieve()
                .body(JsonNode.class);
        return response == null ? 0 : response.path("count").asInt();
    }

    private void assertPersistedAudioAccount(StateContainerEntity account) {
        assertThat(account.getId()).isPositive();
        assertThat(account.getOwnerType()).isEqualTo("USER");
        assertThat(account.getOwnerId()).isEqualTo(1L);
        assertThat(account.getContainerType()).isEqualTo("BANK_ACCOUNT");
        assertThat(account.getName()).isEqualTo("hdfc bank account");
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getCurrency()).isEqualTo("INR");
        assertThat(account.getCurrentValue()).isEqualByComparingTo("40000");
        assertThat(account.getAvailableValue()).isEqualByComparingTo("40000");
        assertThat(account.getCreatedAt()).isNotNull();
        assertThat(account.getUpdatedAt()).isNotNull();
    }

}
