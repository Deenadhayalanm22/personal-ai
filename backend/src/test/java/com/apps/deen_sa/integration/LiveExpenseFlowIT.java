package com.apps.deen_sa.integration;

import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.service.MagicLinkService;
import com.apps.deen_sa.entity.WebSessionEntity;
import com.apps.deen_sa.repository.WebSessionRepository;
import com.apps.deen_sa.domain.MessageSource;
import com.apps.deen_sa.domain.TransactionDraftExtractionStatus;
import com.apps.deen_sa.domain.TransactionDraftStatus;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.TransactionDraftEntity;
import com.apps.deen_sa.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.entity.FinancialTransactionEntity;
import com.apps.deen_sa.domain.SpendingNature;
import com.apps.deen_sa.repository.TransactionDraftExtractionRepository;
import com.apps.deen_sa.repository.TransactionDraftRepository;
import com.apps.deen_sa.repository.FinancialTransactionRepository;
import com.apps.deen_sa.repository.UserReferenceAliasRepository;
import com.apps.deen_sa.repository.UserReferenceEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "live-model"})
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
class LiveExpenseFlowIT {
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

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private WebSessionRepository webSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        printUserMessage("Paid ₹250 for lunch at Rahmaniya Briyani from HDFC Salary Account via UPI");
        userSendsText(user, "wamid.text-2",
                "Paid ₹250 for lunch at Rahmaniya Briyani from HDFC Salary Account via UPI");

        // 5. Application creates a new draft and asks for confirmation again.
        TransactionDraftEntity secondDraft = draft("wamid.text-2");
        TransactionDraftExtractionEntity secondExtraction = activeExtraction(secondDraft);
        assertThat(secondDraft.getStatus()).isEqualTo(TransactionDraftStatus.PENDING);
        assertThat(secondExtraction.getStatus()).isEqualTo(TransactionDraftExtractionStatus.ACTIVE);
        assertThat(secondExtraction.getSourceAccountName())
                .isEqualToIgnoringCase("HDFC Salary Account");
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
        assertThat(referenceEntityRepository.count()).isEqualTo(2);
        assertThat(referenceAliasRepository.count()).isEqualTo(2);
        assertThat(financialTransactionRepository.count()).isEqualTo(1);
        var sourceAccount = referenceEntityRepository
                .findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(
                        secondDraft.getUser().getId(), UserReferenceEntityType.ACCOUNT,
                        "HDFC Salary Account")
                .orElseThrow();
        FinancialTransactionEntity secondTransaction = financialTransactionRepository
                .findBySourceDraftId(secondDraft.getId()).orElseThrow();
        assertThat(secondTransaction.getSourceAccount().getId()).isEqualTo(sourceAccount.getId());
        printExtraction("APPLICATION: Expense confirmed", secondExtraction);

        // 7. The AI can now use previously confirmed merchants and source accounts.
        printUserMessage("bought lunch from nandana palace for 200 using HDFC Salary Account");
        userSendsText(
                user,
                "wamid.text-3",
                "bought lunch from nandana palace for 200 using HDFC Salary Account");

        TransactionDraftEntity thirdDraft = draft("wamid.text-3");
        TransactionDraftExtractionEntity thirdExtraction = activeExtraction(thirdDraft);
        assertThat(thirdDraft.getStatus()).isEqualTo(TransactionDraftStatus.PENDING);
        assertThat(thirdExtraction.getStatus()).isEqualTo(TransactionDraftExtractionStatus.ACTIVE);
        assertThat(thirdExtraction.getAmount()).isEqualByComparingTo("200");
        assertThat(thirdExtraction.getMerchantName()).containsIgnoringCase("Nandana Palace");
        assertThat(thirdExtraction.getSourceAccountName())
                .isEqualToIgnoringCase("HDFC Salary Account");
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
        assertThat(referenceEntityRepository.count()).isEqualTo(3);
        assertThat(referenceAliasRepository.count()).isEqualTo(3);
        assertThat(financialTransactionRepository.count()).isEqualTo(2);
        FinancialTransactionEntity thirdTransaction = financialTransactionRepository
                .findBySourceDraftId(thirdDraft.getId()).orElseThrow();
        assertThat(thirdTransaction.getSourceAccount().getId()).isEqualTo(sourceAccount.getId());
        printExtraction("APPLICATION: Expense confirmed", thirdExtraction);

        // 9. User selects yesterday in the calendar and opens WhatsApp.
        String sessionToken = createWebSession(user);
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);
        createMissingDateContext(sessionToken, yesterday);
        System.out.printf("%nUSER → WEB CALENDAR%nRecord next transaction for %s%n", yesterday);

        // 10. The next message has no date, so the calendar context supplies yesterday.
        printUserMessage("Paid ₹100 for dinner at A2B");
        userSendsText(user, "wamid.text-4", "Paid ₹100 for dinner at A2B");
        TransactionDraftEntity fourthDraft = draft("wamid.text-4");
        TransactionDraftExtractionEntity fourthExtraction = activeExtraction(fourthDraft);
        assertThat(fourthExtraction.getOccurredAt()).isEqualTo(yesterday);
        printExtraction("WHATSAPP → USER: Confirm or Discard", fourthExtraction);

        printUserMessage("Confirm");
        userSelectsButton(
                user,
                "wamid.confirm-4",
                "v2:expense:confirm:" + fourthExtraction.getId(),
                "Confirm");

        fourthDraft = draft("wamid.text-4");
        fourthExtraction = extraction(fourthExtraction.getId());
        assertThat(fourthDraft.getStatus()).isEqualTo(TransactionDraftStatus.CONSUMED);
        assertThat(fourthExtraction.getStatus()).isEqualTo(TransactionDraftExtractionStatus.USED);
        assertThat(financialTransactionRepository
                .findBySourceDraftId(fourthDraft.getId()).orElseThrow().getOccurredAt())
                .isEqualTo(yesterday);
        printExtraction("APPLICATION: Yesterday's expense confirmed", fourthExtraction);

        // 11. Add yesterday expenses across five categories and all three spending natures.
        FinancialTransactionEntity rent = recordYesterdayExpense(
                user, sessionToken, yesterday, "5", "Paid house rent of 16000");
        FinancialTransactionEntity electricity = recordYesterdayExpense(
                user, sessionToken, yesterday, "6", "Paid electricity bill of 1800");
        FinancialTransactionEntity petrol = recordYesterdayExpense(
                user, sessionToken, yesterday, "7", "Filled petrol for 1200");
        FinancialTransactionEntity eatingOut = recordYesterdayExpense(
                user, sessionToken, yesterday, "8", "Paid 400 for dinner at Saravana Bhavan");
        FinancialTransactionEntity clothing = recordYesterdayExpense(
                user, sessionToken, yesterday, "9", "Bought clothing for 2500");
        FinancialTransactionEntity groceries = recordYesterdayExpense(
                user, sessionToken, yesterday, "10", "Bought groceries for 3000");
        FinancialTransactionEntity medicines = recordYesterdayExpense(
                user, sessionToken, yesterday, "11", "Bought medicines for 600");
        FinancialTransactionEntity taxi = recordYesterdayExpense(
                user, sessionToken, yesterday, "12", "Paid 350 for a taxi ride");
        FinancialTransactionEntity haircut = recordYesterdayExpense(
                user, sessionToken, yesterday, "13", "Paid 500 for a haircut");
        FinancialTransactionEntity furniture = recordYesterdayExpense(
                user, sessionToken, yesterday, "14", "Bought furniture for 4200");
        FinancialTransactionEntity books = recordYesterdayExpense(
                user, sessionToken, yesterday, "15", "Bought books for 900");
        FinancialTransactionEntity movies = recordYesterdayExpense(
                user, sessionToken, yesterday, "16", "Paid 700 for movie tickets");
        FinancialTransactionEntity foodDelivery = recordYesterdayExpense(
                user, sessionToken, yesterday, "17", "Paid 650 for food delivery");
        FinancialTransactionEntity gifts = recordYesterdayExpense(
                user, sessionToken, yesterday, "18", "Bought a gift for 1500");

        assertClassification(rent, "Housing", "Rent", SpendingNature.ESSENTIAL);
        assertClassification(electricity, "Utilities", "Electricity", SpendingNature.ESSENTIAL);
        assertClassification(petrol, "Transportation", "Fuel", SpendingNature.ESSENTIAL);
        assertClassification(eatingOut, "Food & Dining", "Restaurant & Cafe",
                SpendingNature.DISCRETIONARY);
        assertClassification(clothing, "Shopping", "Clothing", SpendingNature.FLEXIBLE);
        assertClassification(groceries, "Food & Dining", "Groceries", SpendingNature.ESSENTIAL);
        assertClassification(medicines, "Medical", "Medicines", SpendingNature.ESSENTIAL);
        assertClassification(taxi, "Transportation", "Auto & Taxi", SpendingNature.FLEXIBLE);
        assertClassification(haircut, "Personal Care", "Haircut & Grooming", SpendingNature.FLEXIBLE);
        assertClassification(furniture, "Housing", "Furniture", SpendingNature.FLEXIBLE);
        assertClassification(books, "Education", "Books", SpendingNature.FLEXIBLE);
        assertClassification(movies, "Entertainment", "Movies", SpendingNature.DISCRETIONARY);
        assertClassification(foodDelivery, "Food & Dining", "Food Delivery",
                SpendingNature.DISCRETIONARY);
        assertClassification(gifts, "Shopping", "Gifts", SpendingNature.DISCRETIONARY);

        // 12. The super admin manually triggers catch-up after a skipped scheduled run.
        printUserMessage("/aggregate");
        userSendsText(user, "wamid.aggregate-1", "/aggregate");

        var yesterdayTransaction = financialTransactionRepository
                .findBySourceDraftId(fourthDraft.getId()).orElseThrow();
        List<Map<String, Object>> aggregates = jdbcTemplate.queryForList("""
                SELECT user_id, aggregate_date, category, subcategory, spending_nature,
                       total_amount, transaction_count, min_amount, max_amount
                FROM expense_daily_aggregate
                WHERE aggregate_date = ?
                ORDER BY category, subcategory
                """, yesterday);
        assertThat(aggregates).hasSize(14);
        assertThat(aggregates).allSatisfy(aggregate -> {
            assertThat(aggregate.get("user_id").toString())
                    .isEqualTo(yesterdayTransaction.getUser().getId().toString());
            assertThat(((java.sql.Date) aggregate.get("aggregate_date")).toLocalDate())
                    .isEqualTo(yesterday);
        });
        assertAggregate(aggregates, "Food & Dining", "Restaurant & Cafe",
                "DISCRETIONARY", "500.00", 2, "100.00", "400.00");
        assertAggregate(aggregates, "Housing", "Rent",
                "ESSENTIAL", "16000.00", 1, "16000.00", "16000.00");
        assertAggregate(aggregates, "Utilities", "Electricity",
                "ESSENTIAL", "1800.00", 1, "1800.00", "1800.00");
        assertAggregate(aggregates, "Transportation", "Fuel",
                "ESSENTIAL", "1200.00", 1, "1200.00", "1200.00");
        assertAggregate(aggregates, "Shopping", "Clothing",
                "FLEXIBLE", "2500.00", 1, "2500.00", "2500.00");

        List<Map<String, Object>> natureTotals = jdbcTemplate.queryForList("""
                SELECT spending_nature,
                       SUM(total_amount) AS total_amount,
                       SUM(transaction_count) AS transaction_count
                FROM expense_daily_aggregate
                WHERE aggregate_date = ?
                GROUP BY spending_nature
                ORDER BY spending_nature
                """, yesterday);
        assertNatureAggregate(natureTotals, "ESSENTIAL", "22600.00", 5);
        assertNatureAggregate(natureTotals, "FLEXIBLE", "8450.00", 5);
        assertNatureAggregate(natureTotals, "DISCRETIONARY", "3350.00", 5);
        System.out.printf("%nAPPLICATION: Aggregate backfill completed%n%s%n", aggregates);

        // 13. Repeating the command finds no missing dates and creates no duplicates.
        printUserMessage("/aggregate");
        userSendsText(user, "wamid.aggregate-2", "/aggregate");
        Integer aggregateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expense_daily_aggregate WHERE aggregate_date = ?",
                Integer.class, yesterday);
        assertThat(aggregateCount).isEqualTo(14);

        // Admin commands bypass draft creation and OpenAI extraction.
        assertThat(draftRepository.count()).isEqualTo(18);
        assertThat(extractionRepository.count()).isEqualTo(18);
        assertThat(financialTransactionRepository.count()).isEqualTo(17);

        // 14. Preferred account normalization resolves named and unambiguous generic cards.
        String accountReferenceUser = "919876543211";

        printUserMessage("Paid 100 for lunch using HDFC bank account");
        userSendsText(accountReferenceUser, "wamid.account-ref-bank",
                "Paid 100 for lunch using HDFC bank account");
        TransactionDraftExtractionEntity bankAccount =
                activeExtraction(draft("wamid.account-ref-bank"));
        assertThat(bankAccount.getSourceAccountName())
                .isEqualToIgnoringCase("HDFC bank account");
        userSelectsButton(accountReferenceUser, "wamid.account-ref-confirm-bank",
                "v2:expense:confirm:" + bankAccount.getId(), "Confirm");

        printUserMessage("Did electricity payment of 922 from cred app paid using hdfc credit card");
        userSendsText(accountReferenceUser, "wamid.account-ref-1",
                "Did electricity payment of 922 from cred app paid using hdfc credit card");
        TransactionDraftExtractionEntity namedCard = activeExtraction(draft("wamid.account-ref-1"));
        assertThat(namedCard.getSourceAccountName()).isEqualToIgnoringCase("HDFC credit card");
        printExtraction("WHATSAPP → USER: Named card identified", namedCard);
        Long accountReferenceUserId = appUserRepository
                .findByChannelAndExternalUserId("WHATSAPP", accountReferenceUser)
                .orElseThrow()
                .getId();

        userSelectsButton(accountReferenceUser, "wamid.account-ref-confirm-1",
                "v2:expense:confirm:" + namedCard.getId(), "Confirm");
        String canonicalHdfcCard = referenceEntityRepository
                .findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(
                        accountReferenceUserId,
                        UserReferenceEntityType.ACCOUNT,
                        "HDFC credit card")
                .orElseThrow()
                .getCanonicalName();

        printUserMessage("Paid 500 for groceries using credit card");
        userSendsText(accountReferenceUser, "wamid.account-ref-2",
                "Paid 500 for groceries using credit card");
        TransactionDraftExtractionEntity genericCard =
                activeExtraction(draft("wamid.account-ref-2"));
        assertThat(genericCard.getSourceAccountName()).isEqualTo(canonicalHdfcCard);
        printExtraction("WHATSAPP → USER: Generic card resolved", genericCard);
        userSelectsButton(accountReferenceUser, "wamid.account-ref-discard-2",
                "v2:expense:discard:" + genericCard.getId(), "Discard");

        printUserMessage("Paid 250 for lunch using HDFC card");
        userSendsText(accountReferenceUser, "wamid.account-ref-partial",
                "Paid 250 for lunch using HDFC card");
        TransactionDraftExtractionEntity partialCard =
                activeExtraction(draft("wamid.account-ref-partial"));
        assertThat(partialCard.getSourceAccountName()).isEqualTo(canonicalHdfcCard);
        printExtraction("WHATSAPP → USER: Partial card reference resolved", partialCard);
        userSelectsButton(accountReferenceUser, "wamid.account-ref-discard-partial",
                "v2:expense:discard:" + partialCard.getId(), "Discard");

        printUserMessage("Paid 700 for fuel using ICICI credit card");
        userSendsText(accountReferenceUser, "wamid.account-ref-3",
                "Paid 700 for fuel using ICICI credit card");
        TransactionDraftExtractionEntity secondNamedCard =
                activeExtraction(draft("wamid.account-ref-3"));
        assertThat(secondNamedCard.getSourceAccountName())
                .isEqualToIgnoringCase("ICICI credit card");
        printExtraction("WHATSAPP → USER: Second named card identified", secondNamedCard);
        userSelectsButton(accountReferenceUser, "wamid.account-ref-confirm-3",
                "v2:expense:confirm:" + secondNamedCard.getId(), "Confirm");

        printUserMessage("Paid 300 for dinner using credit card");
        userSendsText(accountReferenceUser, "wamid.account-ref-4",
                "Paid 300 for dinner using credit card");
        TransactionDraftExtractionEntity ambiguousCard =
                activeExtraction(draft("wamid.account-ref-4"));
        assertThat(ambiguousCard.getSourceAccountName()).isNull();
        printExtraction("WHATSAPP → USER: Ambiguous generic card not guessed", ambiguousCard);

        // 15. User creates a beneficiary preference with multiple aliases from the web app.
        String referenceSessionToken = createWebSession(accountReferenceUser);
        mockMvc.perform(post("/api/web/reference-preferences")
                        .cookie(new jakarta.servlet.http.Cookie(
                                "WEB_SESSION", referenceSessionToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entityType": "BENEFICIARY",
                                  "primaryReference": "Deena",
                                  "alias": "Deena S, DS, deena s"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entityType").value("BENEFICIARY"))
                .andExpect(jsonPath("$.primaryReference").value("Deena"))
                .andExpect(jsonPath("$.aliases.length()").value(2))
                .andExpect(jsonPath("$.aliases[0].alias").value("Deena S"))
                .andExpect(jsonPath("$.aliases[1].alias").value("DS"));

        var beneficiary = referenceEntityRepository
                .findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(
                        accountReferenceUserId,
                        UserReferenceEntityType.BENEFICIARY,
                        "Deena")
                .orElseThrow();
        assertThat(beneficiary.isActive()).isTrue();
        assertThat(referenceAliasRepository.findByReferenceEntityId(beneficiary.getId()))
                .extracting(alias -> alias.getAliasText())
                .containsExactlyInAnyOrder("Deena S", "DS");

        // 16. Web app lists the user's primary references with their aliases.
        mockMvc.perform(get("/api/web/reference-preferences")
                        .cookie(new jakarta.servlet.http.Cookie(
                                "WEB_SESSION", referenceSessionToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.references[?(@.referenceId == %s)].entityType"
                                .formatted(beneficiary.getId()))
                        .value(org.hamcrest.Matchers.hasItem("BENEFICIARY")))
                .andExpect(jsonPath("$.references[?(@.referenceId == %s)].primaryReference"
                                .formatted(beneficiary.getId()))
                        .value(org.hamcrest.Matchers.hasItem("Deena")))
                .andExpect(jsonPath("$.references[?(@.referenceId == %s)].aliases[*].alias"
                                .formatted(beneficiary.getId()))
                        .value(org.hamcrest.Matchers.containsInAnyOrder("DS", "Deena S")));
    }

    private FinancialTransactionEntity recordYesterdayExpense(
            String user,
            String sessionToken,
            LocalDate yesterday,
            String sequence,
            String message
    ) throws Exception {
        createMissingDateContext(sessionToken, yesterday);
        printUserMessage(message);
        String textMessageId = "wamid.text-" + sequence;
        userSendsText(user, textMessageId, message);
        TransactionDraftEntity draft = draft(textMessageId);
        TransactionDraftExtractionEntity extraction = activeExtraction(draft);
        assertThat(extraction.getOccurredAt()).isEqualTo(yesterday);
        printExtraction("WHATSAPP → USER: Confirm or Discard", extraction);

        printUserMessage("Confirm");
        userSelectsButton(user, "wamid.confirm-" + sequence,
                "v2:expense:confirm:" + extraction.getId(), "Confirm");
        assertThat(extraction(extraction.getId()).getStatus())
                .isEqualTo(TransactionDraftExtractionStatus.USED);
        return financialTransactionRepository.findBySourceDraftId(draft.getId()).orElseThrow();
    }

    private void assertClassification(
            FinancialTransactionEntity transaction,
            String category,
            String subcategory,
            SpendingNature spendingNature
    ) {
        assertThat(transaction.getCategory()).isEqualTo(category);
        assertThat(transaction.getSubcategory()).isEqualTo(subcategory);
        assertThat(transaction.getSpendingNature()).isEqualTo(spendingNature);
    }

    private void assertAggregate(
            List<Map<String, Object>> aggregates,
            String category,
            String subcategory,
            String spendingNature,
            String total,
            int count,
            String minimum,
            String maximum
    ) {
        Map<String, Object> aggregate = aggregates.stream()
                .filter(row -> category.equals(row.get("category"))
                        && subcategory.equals(row.get("subcategory")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing aggregate for " + category + " / " + subcategory));
        assertThat(aggregate.get("spending_nature")).isEqualTo(spendingNature);
        assertThat((BigDecimal) aggregate.get("total_amount")).isEqualByComparingTo(total);
        assertThat(((Number) aggregate.get("transaction_count")).intValue()).isEqualTo(count);
        assertThat((BigDecimal) aggregate.get("min_amount")).isEqualByComparingTo(minimum);
        assertThat((BigDecimal) aggregate.get("max_amount")).isEqualByComparingTo(maximum);
    }

    private void assertNatureAggregate(
            List<Map<String, Object>> natureTotals,
            String spendingNature,
            String total,
            int transactionCount
    ) {
        Map<String, Object> aggregate = natureTotals.stream()
                .filter(row -> spendingNature.equals(row.get("spending_nature")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing spending nature aggregate for " + spendingNature));
        assertThat((BigDecimal) aggregate.get("total_amount")).isEqualByComparingTo(total);
        assertThat(((Number) aggregate.get("transaction_count")).intValue())
                .isEqualTo(transactionCount);
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

    private String createWebSession(String externalUserId) {
        long userId = appUserRepository
                .findByChannelAndExternalUserId("WHATSAPP", externalUserId)
                .orElseThrow()
                .getId();
        String token = "live-v2-session-" + externalUserId;
        WebSessionEntity session = new WebSessionEntity();
        session.setTokenHash(MagicLinkService.hash(token));
        session.setUserId(userId);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        webSessionRepository.saveAndFlush(session);
        return token;
    }

    private void createMissingDateContext(String sessionToken, LocalDate date) throws Exception {
        mockMvc.perform(post("/api/web/expenses/calendar/context")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", sessionToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"MISSING_TRANSACTION_DATE",
                                  "date":"%s",
                                  "timezone":"Asia/Kolkata"
                                }
                                """.formatted(date)))
                .andExpect(status().isCreated());
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
                sourceAccount: %s
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
                extraction.getSourceAccountName(),
                extraction.getCategoryId(),
                extraction.getSubcategoryId(),
                extraction.getOccurredAt(),
                extraction.getConfidence());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is required to run the real-provider LiveExpenseFlowIT test");
        }
        return value;
    }
}
