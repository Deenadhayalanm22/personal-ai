package com.apps.deen_sa.v2.integration;

import com.apps.deen_sa.integration.PostgresTestContainerInitializer;
import com.apps.deen_sa.v2.domain.MessageSource;
import com.apps.deen_sa.v2.domain.TransactionDraftExtractionStatus;
import com.apps.deen_sa.v2.domain.TransactionDraftStatus;
import com.apps.deen_sa.v2.entity.TransactionDraftEntity;
import com.apps.deen_sa.v2.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.v2.repository.TransactionDraftExtractionRepository;
import com.apps.deen_sa.v2.repository.TransactionDraftRepository;
import com.apps.deen_sa.v2.repository.FinancialTransactionRepository;
import com.apps.deen_sa.v2.repository.UserReferenceAliasRepository;
import com.apps.deen_sa.v2.repository.UserReferenceEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "live-model"})
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
class LiveV2IT {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionDraftRepository draftRepository;

    @Autowired
    private TransactionDraftExtractionRepository extractionRepository;

    @Autowired
    private UserReferenceEntityRepository referenceEntityRepository;

    @Autowired
    private UserReferenceAliasRepository referenceAliasRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @DynamicPropertySource
    static void liveProviderProperties(DynamicPropertyRegistry properties) {
        properties.add("openai.api-key", () -> requiredEnvironment("OPENAI_API_KEY"));
    }

    @Test
    void test_001() throws Exception {
        String user = "919876543210";

        // 1. User sends the first expense.
        printUserMessage("Paid ₹250 for lunch at Star Briyani");
        userSendsText(user, "wamid.text-1", "Paid ₹250 for lunch at Star Briyani");

        // 2. Application saves and extracts it, then WhatsApp shows Confirm / Discard.
        TransactionDraftEntity firstDraft = draft("wamid.text-1");
        TransactionDraftExtractionEntity firstExtraction = activeExtraction(firstDraft);
        assertThat(firstDraft.getStatus()).isEqualTo(TransactionDraftStatus.PENDING);
        assertThat(firstExtraction.getStatus()).isEqualTo(TransactionDraftExtractionStatus.ACTIVE);
        printExtraction("WHATSAPP → USER: Confirm or Discard", firstExtraction);

        // 3. User selects Discard through the WhatsApp webhook.
        printUserMessage("Discard");
        userSelectsButton(
                user,
                "wamid.discard-1",
                "v2:expense:discard:" + firstExtraction.getId(),
                "Discard");

        firstDraft = draft("wamid.text-1");
        firstExtraction = extraction(firstExtraction.getId());
        assertThat(firstDraft.getStatus()).isEqualTo(TransactionDraftStatus.CANCELLED);
        assertThat(firstExtraction.getStatus()).isEqualTo(TransactionDraftExtractionStatus.REJECTED);
        assertThat(referenceEntityRepository.count()).isZero();
        assertThat(referenceAliasRepository.count()).isZero();
        assertThat(financialTransactionRepository.count()).isZero();
        printExtraction("APPLICATION: Expense rejected", firstExtraction);

        // 4. User sends a new expense after rejecting the first one.
        printUserMessage("Paid ₹250 for lunch at Rahmaniya Briyani");
        userSendsText(user, "wamid.text-2", "Paid ₹250 for lunch at Rahmaniya Briyani");

        // 5. Application creates a new draft and asks for confirmation again.
        TransactionDraftEntity secondDraft = draft("wamid.text-2");
        TransactionDraftExtractionEntity secondExtraction = activeExtraction(secondDraft);
        assertThat(secondDraft.getStatus()).isEqualTo(TransactionDraftStatus.PENDING);
        assertThat(secondExtraction.getStatus()).isEqualTo(TransactionDraftExtractionStatus.ACTIVE);
        printExtraction("WHATSAPP → USER: Confirm or Discard", secondExtraction);

        // 6. User selects Confirm through the WhatsApp webhook.
        printUserMessage("Confirm");
        userSelectsButton(
                user,
                "wamid.confirm-2",
                "v2:expense:confirm:" + secondExtraction.getId(),
                "Confirm");

        secondDraft = draft("wamid.text-2");
        secondExtraction = extraction(secondExtraction.getId());
        assertThat(secondDraft.getStatus()).isEqualTo(TransactionDraftStatus.CONSUMED);
        assertThat(secondExtraction.getStatus()).isEqualTo(TransactionDraftExtractionStatus.USED);
        assertThat(referenceEntityRepository.count()).isEqualTo(1);
        assertThat(referenceAliasRepository.count()).isEqualTo(1);
        assertThat(financialTransactionRepository.count()).isEqualTo(1);
        printExtraction("APPLICATION: Expense confirmed", secondExtraction);

        // 7. User sends another expense. The AI can now use previously confirmed merchants.
        printUserMessage("bought lunch from nandana palace for 200");
        userSendsText(
                user,
                "wamid.text-3",
                "bought lunch from nandana palace for 200");

        TransactionDraftEntity thirdDraft = draft("wamid.text-3");
        TransactionDraftExtractionEntity thirdExtraction = activeExtraction(thirdDraft);
        assertThat(thirdDraft.getStatus()).isEqualTo(TransactionDraftStatus.PENDING);
        assertThat(thirdExtraction.getStatus()).isEqualTo(TransactionDraftExtractionStatus.ACTIVE);
        assertThat(thirdExtraction.getAmount()).isEqualByComparingTo("200");
        assertThat(thirdExtraction.getMerchantName()).containsIgnoringCase("Nandana Palace");
        printExtraction("WHATSAPP → USER: Confirm or Discard", thirdExtraction);

        // 8. User confirms the Nandana Palace expense through WhatsApp.
        printUserMessage("Confirm");
        userSelectsButton(
                user,
                "wamid.confirm-3",
                "v2:expense:confirm:" + thirdExtraction.getId(),
                "Confirm");

        thirdDraft = draft("wamid.text-3");
        thirdExtraction = extraction(thirdExtraction.getId());
        assertThat(thirdDraft.getStatus()).isEqualTo(TransactionDraftStatus.CONSUMED);
        assertThat(thirdExtraction.getStatus()).isEqualTo(TransactionDraftExtractionStatus.USED);
        assertThat(referenceEntityRepository.count()).isEqualTo(2);
        assertThat(referenceAliasRepository.count()).isEqualTo(2);
        assertThat(financialTransactionRepository.count()).isEqualTo(2);
        printExtraction("APPLICATION: Expense confirmed", thirdExtraction);

        assertThat(draftRepository.count()).isEqualTo(3);
        assertThat(extractionRepository.count()).isEqualTo(3);
    }

    private void postWebhook(String webhook) throws Exception {
        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhook))
                .andExpect(status().isOk());
    }

    private void userSendsText(String user, String messageId, String message) throws Exception {
        postWebhook("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "id":"%s",
                  "from":"%s",
                  "type":"text",
                  "text":{"body":"%s"}
                }]}}]}]}
                """.formatted(messageId, user, message));
    }

    private void userSelectsButton(
            String user,
            String messageId,
            String buttonId,
            String title
    ) throws Exception {
        postWebhook("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "id":"%s",
                  "from":"%s",
                  "type":"interactive",
                  "interactive":{"button_reply":{"id":"%s","title":"%s"}}
                }]}}]}]}
                """.formatted(messageId, user, buttonId, title));
    }

    private TransactionDraftEntity draft(String messageId) {
        return draftRepository.findBySourceAndSourceMessageId(MessageSource.WHATSAPP, messageId)
                .orElseThrow();
    }

    private TransactionDraftExtractionEntity activeExtraction(TransactionDraftEntity draft) {
        return extractionRepository.findByDraftIdAndStatus(
                        draft.getId(), TransactionDraftExtractionStatus.ACTIVE)
                .orElseThrow();
    }

    private TransactionDraftExtractionEntity extraction(long extractionId) {
        return extractionRepository.findById(extractionId).orElseThrow();
    }

    private void printUserMessage(String message) {
        System.out.printf("%nUSER → WHATSAPP%n%s%n", message);
    }

    private void printExtraction(String heading, TransactionDraftExtractionEntity extraction) {
        System.out.printf("""

                %s
                --------------------------------
                extractionId : %s
                draftId      : %s
                status       : %s
                amount       : %s
                merchant     : %s
                category     : %s
                subcategory  : %s
                occurredAt   : %s
                confidence   : %s
                --------------------------------
                %n""",
                heading,
                extraction.getId(),
                extraction.getDraft().getId(),
                extraction.getStatus(),
                extraction.getAmount(),
                extraction.getMerchantName(),
                extraction.getCategoryId(),
                extraction.getSubcategoryId(),
                extraction.getOccurredAt(),
                extraction.getConfidence());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is required to run the real-provider LiveV2IT test");
        }
        return value;
    }
}
