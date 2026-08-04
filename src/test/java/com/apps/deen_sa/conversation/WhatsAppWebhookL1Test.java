package com.apps.deen_sa.conversation;

import com.apps.deen_sa.core.state.StateChangeEntity;
import com.apps.deen_sa.integration.AbstractIntegrationTestProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("Layer1")
@SpringBootTest
@AutoConfigureMockMvc
class WhatsAppWebhookL1Test extends AbstractIntegrationTestProperties {

    private static final String USER_PHONE_NUMBER = "919876543210";
    private static final String EXPENSE_TEXT = "Spent 500 on groceries at BigBasket";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void verifiesWhatsAppWebhookChallenge() throws Exception {
        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "my-tellme-app-token")
                        .param("hub.challenge", "integration-challenge"))
                .andExpect(status().isOk())
                .andExpect(content().string("integration-challenge"));
    }

    @Test
    void receivesWhatsAppExpenseAndPersistsItThroughTheApplicationFlow() throws Exception {
        String payload = """
                {
                  "entry": [
                    {
                      "changes": [
                        {
                          "value": {
                            "messages": [
                              {
                                "from": "%s",
                                "type": "text",
                                "text": {"body": "%s"}
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(USER_PHONE_NUMBER, EXPENSE_TEXT);

        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

      await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(250))
        .untilAsserted(() -> assertThat(stateChangeRepository.findAll())
          .anySatisfy(transaction -> assertPersistedExpense(transaction)));
        }

        private void assertPersistedExpense(StateChangeEntity transaction) {
        assertThat(transaction.getUserId()).isEqualTo("1");
      assertThat(transaction.getRawText()).contains(EXPENSE_TEXT);
        assertThat(transaction.getAmount()).isEqualByComparingTo("500.00");
        assertThat(transaction.getTransactionType()).hasToString("EXPENSE");
        assertThat(transaction.getCategory()).isNotBlank();
        assertThat(transaction.getTimestamp()).isNotNull();
        assertThat(transaction.getCreatedAt()).isNotNull();
    }

}
