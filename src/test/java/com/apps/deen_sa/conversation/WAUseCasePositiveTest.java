package com.apps.deen_sa.conversation;

import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("Layer1")
@SpringBootTest
@AutoConfigureMockMvc
class WAUseCasePositiveTest extends AbstractIntegrationTestProperties {

    private static final String USER_PHONE_NUMBER = "919876543210";
    private static final String EXPENSE_TEXT = "Paid house rent of 16000 via bank transfer";
        private static final String SALARY_ACCOUNT_TEXT =
          "Setup my hdfc bank account where i have currently 40k balance as of now";
        private static final String CREDIT_CARD_TEXT =
          "Setup my hdfc credit card where i have 30k inr limit outstanding of 0 and due date is 21 of every month";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StateContainerRepository stateContainerRepository;

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
                        .anySatisfy(this::assertPersistedExpense));
    }

    @Test
    void continuouslySetsUpSalaryAccountAndCreditCardFromWhatsAppMessages() throws Exception {
        postWhatsAppMessage(SALARY_ACCOUNT_TEXT);
        await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> assertThat(stateContainerRepository.findActiveByOwnerId(1L))
                        .anySatisfy(this::assertSalaryAccount));

        postWhatsAppMessage(CREDIT_CARD_TEXT);
        await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    assertThat(stateContainerRepository.findActiveByOwnerId(1L))
                            .hasSize(2)
                            .anySatisfy(this::assertSalaryAccount)
                            .anySatisfy(this::assertCreditCard);
                });
    }

    private void postWhatsAppMessage(String message) throws Exception {
        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "from": "%s",
                          "type": "text",
                          "text": {"body": "%s"}
                        }]
                      }
                    }]
                  }]
                }
                """.formatted(USER_PHONE_NUMBER, message);

        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private void assertSalaryAccount(StateContainerEntity account) {
        assertThat(account.getContainerType()).isEqualTo("BANK_ACCOUNT");
        assertThat(account.getName()).isEqualTo("hdfc bank account");
        assertThat(account.getCurrency()).isEqualTo("INR");
        assertThat(account.getCurrentValue()).isEqualByComparingTo("40000.00");
        assertThat(account.getAvailableValue()).isEqualByComparingTo("40000.00");
    }

    private void assertCreditCard(StateContainerEntity account) {
        assertThat(account.getContainerType()).isEqualTo("CREDIT_CARD");
        assertThat(account.getName()).isEqualTo("hdfc credit card");
        assertThat(account.getCurrency()).isEqualTo("INR");
        assertThat(account.getCapacityLimit()).isEqualByComparingTo("30000.00");
        assertThat(account.getDetails()).containsEntry("dueDay", 21);
    }

    private void assertPersistedExpense(StateChangeEntity transaction) {
        assertThat(transaction.getUserId()).isEqualTo("1");
        assertThat(transaction.getRawText()).contains(EXPENSE_TEXT);
        assertThat(transaction.getAmount()).isEqualByComparingTo("16000.00");
        assertThat(transaction.getTransactionType()).hasToString("EXPENSE");
        assertThat(transaction.getCategory()).isNotBlank();
        assertThat(transaction.getTimestamp()).isNotNull();
        assertThat(transaction.getCreatedAt()).isNotNull();
    }

}
