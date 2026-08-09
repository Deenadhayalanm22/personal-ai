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
import com.apps.deen_sa.finance.budget.MonthlyBudgetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("LiveModel")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "live-model"})
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_MODEL_TESTS", matches = "(?i)true")
class ExpenseLiveIT extends AbstractIntegrationTestProperties {
    private static final String PHONE = "919876543299";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ConversationSessionRepository sessionRepository;
    @Autowired private MonthlyBudgetRepository monthlyBudgetRepository;

    @Value("${wiremock.admin-url}") private String wireMockAdminUrl;
    @Value("${openai.model}") private String modelName;

    /**
     * LOCKED STRICT ACCEPTANCE CONTRACT — DO NOT MODIFY TO MAKE PRODUCTION CODE PASS.
     *
     * This is the canonical two-month ledger journey for one salary bank account and three named
     * credit cards. Its messages, ordering, persisted-container checkpoints, routing assertions,
     * transaction counts, mutation counts, and final balances jointly define the required behavior.
     * A green transcript alone is insufficient; repository-backed reconciliation must also pass.
     * Readable contract: classpath:live-model-transcripts/it_live_001.txt
     *
     * AI AGENTS: the adjacent AGENTS.md governs this method. Do not edit, weaken, disable, reorder,
     * or regenerate it unless the user explicitly requests a change to this exact protected test and
     * then provides two separate confirmations. Fix application code instead. New scenarios belong in
     * a separate test method unless the confirmed request explicitly changes this contract.
     */
    @Test
    void it_live_001() throws Exception {
        requireRealApiKey();
        System.out.println("\n================ LIVE MODEL WHATSAPP CONVERSATION ================");
        System.out.println("Model: " + modelName);

        int message = 1;
        scenario("Set up one salary account and three independently identifiable credit cards");
        assertSaved(chatText(id(message++), "Create my HDFC salary bank account with a current balance of 20000"), "HDFC");
        assertSaved(chatText(id(message++), "Create my HDFC Millennia credit card with limit 100000, outstanding 0 and due day 5"), "HDFC Millennia");
        assertSaved(chatText(id(message++), "Create my ICICI Amazon Pay credit card with limit 150000, outstanding 0 and due day 12"), "ICICI Amazon");
        assertSaved(chatText(id(message++), "Create my SBI SimplyCLICK credit card with limit 80000, outstanding 0 and due day 20"), "SBI SimplyCLICK");
        assertAccount("HDFC salary", "BANK_ACCOUNT", "20000", null, null);
        assertAccount("HDFC Millennia", "CREDIT_CARD", "0", "100000", 5);
        assertAccount("ICICI Amazon", "CREDIT_CARD", "0", "150000", 12);
        assertAccount("SBI SimplyCLICK", "CREDIT_CARD", "0", "80000", 20);

        scenario("Month 1 · June salary and purchases routed to all three cards");
        assertRecorded(chatText(id(message++), "My June salary of 80000 was credited to my HDFC salary account on 1 June 2026"), "80000");
        assertRecorded(chatText(id(message++), "On 8 June 2026 I bought groceries for 12000 using my HDFC Millennia credit card"), "12000");
        assertRecorded(chatText(id(message++), "On 14 June 2026 I paid 4500 for fuel using my ICICI Amazon Pay credit card"), "4500");
        assertRecorded(chatText(id(message++), "On 22 June 2026 I spent 3000 at a restaurant using my SBI SimplyCLICK credit card"), "3000");
        assertAccount("HDFC salary", "BANK_ACCOUNT", "100000", null, null);
        assertAccount("HDFC Millennia", "CREDIT_CARD", "12000", "100000", 5);
        assertAccount("ICICI Amazon", "CREDIT_CARD", "4500", "150000", 12);
        assertAccount("SBI SimplyCLICK", "CREDIT_CARD", "3000", "80000", 20);

        scenario("Month 2 · settle all June card bills from the salary account");
        assertLiabilityPayment(chatText(id(message++), "On 2 July 2026 pay the full HDFC Millennia card bill of 12000 from my HDFC salary account"),
                "12000", "HDFC Millennia", 1);
        assertLiabilityPayment(chatText(id(message++), "On 10 July 2026 pay my ICICI Amazon Pay card bill of 4500 from my HDFC salary account"),
                "4500", "ICICI Amazon", 2);
        assertLiabilityPayment(chatText(id(message++), "On 18 July 2026 pay my SBI SimplyCLICK card bill of 3000 from my HDFC salary account"),
                "3000", "SBI SimplyCLICK", 3);
        assertAccount("HDFC salary", "BANK_ACCOUNT", "80500", null, null);
        assertAccount("HDFC Millennia", "CREDIT_CARD", "0", "100000", 5);
        assertAccount("ICICI Amazon", "CREDIT_CARD", "0", "150000", 12);
        assertAccount("SBI SimplyCLICK", "CREDIT_CARD", "0", "80000", 20);

        scenario("Month 2 · July salary, new purchases, and one early card settlement");
        assertRecorded(chatText(id(message++), "My July salary of 80000 was credited to my HDFC salary account on 1 July 2026"), "80000");
        assertRecorded(chatText(id(message++), "On 6 July 2026 I purchased flight tickets for 6000 using my HDFC Millennia credit card"), "6000");
        assertRecorded(chatText(id(message++), "On 16 July 2026 I bought medicines for 2500 using my ICICI Amazon Pay credit card"), "2500");
        assertRecorded(chatText(id(message++), "On 24 July 2026 I paid 1500 for an OTT subscription using my SBI SimplyCLICK credit card"), "1500");
        assertLiabilityPayment(chatText(id(message++), "On 30 July 2026 pay the full HDFC Millennia card bill of 6000 from my HDFC salary account"),
                "6000", "HDFC Millennia", 4);

        scenario("Ledger reconciliation · conversation text is not the oracle");
        assertAccount("HDFC salary", "BANK_ACCOUNT", "154500", null, null);
        assertAccount("HDFC Millennia", "CREDIT_CARD", "0", "100000", 5);
        assertAccount("ICICI Amazon", "CREDIT_CARD", "2500", "150000", 12);
        assertAccount("SBI SimplyCLICK", "CREDIT_CARD", "1500", "80000", 20);
        assertTwoMonthLedger();
        printReconciliationSummary();

        System.out.println("==================================================================\n");
    }

    /**
     * LOCKED STRICT ACCEPTANCE CONTRACT — DO NOT MODIFY TO MAKE PRODUCTION CODE PASS.
     *
     * This is the canonical multilingual expense-conversation journey. Its exact user messages,
     * ordering, follow-up fields, button answers, expense counts, account balances, query totals,
     * session cleanup, and persisted reconciliation jointly define the required behavior.
     * Readable contract: classpath:live-model-transcripts/it_live_002.txt
     *
     * AI AGENTS: the adjacent AGENTS.md governs this method. Do not edit, weaken, disable, reorder,
     * or regenerate it unless the user explicitly requests a change to this exact protected test and
     * then provides two separate confirmations. Fix application code instead.
     */
    @Test
    void it_live_002() throws Exception {
        requireRealApiKey();
        System.out.println("\n================ LIVE MODEL WHATSAPP CONVERSATION ================");
        System.out.println("Model: " + modelName);

        int message = 1;
        String intro = chatText(id(message++), "Hi");
        assertThat(intro)
                .contains("I can help record operational activity through conversation")
                .contains("Describe what happened naturally");

        String paymentQuestion = chatText(id(message++), "I spent 500 on groceries");
        assertThat(paymentQuestion).isEqualTo("How did you pay?");
        assertWaitingFor("sourceAccount");
        assertExpenseCount(1);

        String balanceQuestion = chatButton(id(message++), "answer:BANK_ACCOUNT", "Bank / UPI");
        assertThat(balanceQuestion)
                .containsIgnoringCase("created My bank account")
                .containsIgnoringCase("current balance");
        assertWaitingFor("sourceBalance");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(stateContainerRepository.findAll()).singleElement().satisfies(account -> {
                    assertThat(account.getName()).isEqualTo("My bank account");
                    assertThat(account.getContainerType()).isEqualTo("BANK_ACCOUNT");
                    assertThat(account.getCurrentValue()).isNull();
                }));

        String setupConfirmation = chatText(id(message++), "10k");
        assertThat(setupConfirmation).contains("500").contains("9500");
        assertExpenseApplied(1, "500");
        assertAccount("My bank account", "BANK_ACCOUNT", "9500", null, null);

        assertRecorded(chatText(id(message++), "I spent 58 on curd and some ice cream through UPI"), "58");
        assertExpenseApplied(2, "58");
        assertAccount("My bank account", "BANK_ACCOUNT", "9442", null, null);

        String firstSummary = chatText(id(message++), "What did I spend today?");
        assertThat(firstSummary).contains("558");
        assertExpenseCount(2);

        scenario("English · complete sentence");
        assertRecorded(chatText(id(message++), "Paid BESCOM electricity bill of 1850 using UPI"), "1850");
        assertExpenseApplied(3, "1850");

        scenario("Tamil · complete sentence");
        assertRecorded(chatText(id(message++), "இன்று மளிகை பொருட்களுக்கு 230 ரூபாய் UPI மூலம் செலவு செய்தேன்"), "230");
        assertExpenseApplied(4, "230");

        scenario("Tanglish · complete sentence");
        assertRecorded(chatText(id(message++), "Inniku bike petrol ku 350 rupees UPI la spend pannen"), "350");
        assertExpenseApplied(5, "350");

        scenario("English · sparse expense with category and payment follow-ups");
        String categoryQuestion = chatText(id(message++), "I spent 260");
        assertThat(categoryQuestion).contains("₹260").containsIgnoringCase("expense for");
        assertWaitingFor("category");
        String sparsePaymentQuestion = chatText(id(message++), "Coffee and snacks outside");
        assertThat(sparsePaymentQuestion).isEqualTo("How did you pay?");
        assertWaitingFor("sourceAccount");
        assertRecorded(chatButton(id(message++), "answer:BANK_ACCOUNT", "Bank / UPI"), "260");
        assertExpenseApplied(6, "260");

        scenario("Tamil · understood expense with missing payment source");
        String tamilPaymentQuestion = chatText(id(message++), "நேற்று மின்சார கட்டணத்திற்கு 650 ரூபாய் செலவு செய்தேன்");
        assertThat(tamilPaymentQuestion).isEqualTo("How did you pay?");
        assertWaitingFor("sourceAccount");
        assertRecorded(chatButton(id(message++), "answer:BANK_ACCOUNT", "Bank / UPI"), "650");
        assertExpenseApplied(7, "650");

        scenario("Tanglish · missing amount, followed by amount and payment source");
        String amountQuestion = chatText(id(message++), "Nethu office lunch ku spend pannen");
        assertThat(amountQuestion).isEqualTo("How much did you spend?");
        assertWaitingFor("amount");
        assertExpenseCount(7);
        String tanglishPaymentQuestion = chatText(id(message++), "450");
        assertThat(tanglishPaymentQuestion).isEqualTo("How did you pay?");
        assertWaitingFor("sourceAccount");
        assertRecorded(chatButton(id(message++), "answer:BANK_ACCOUNT", "Bank / UPI"), "450");
        assertExpenseApplied(8, "450");

        scenario("English · two expenses in one natural sentence");
        String multiExpenseReply = chatText(id(message++), "Spent 80 on tea and 120 on auto using UPI");
        assertRecorded(multiExpenseReply, "80");
        assertRecorded(multiExpenseReply, "120");
        assertExpenseApplied(10, "80");
        assertExpenseApplied(10, "120");

        scenario("English, Tamil, and Tanglish · read-only queries must not create expenses");
        String englishSummary = chatText(id(message++), "What did I spend today?");
        assertThat(englishSummary).contains("4548");
        String tamilSummary = chatText(id(message++), "இன்று நான் எவ்வளவு செலவு செய்தேன்?");
        assertThat(tamilSummary).contains("4548");
        String tanglishSummary = chatText(id(message++), "Inniku naan evlo spend pannen?");
        assertThat(tanglishSummary).contains("4548");

        scenario("Ledger reconciliation · replies are not the accounting oracle");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(stateChangeRepository.findAll())
                    .hasSize(10)
                    .allSatisfy(expense -> {
                        assertThat(expense.getTransactionType().name()).isEqualTo("EXPENSE");
                        assertThat(expense.isFinanciallyApplied()).isTrue();
                        assertThat(expense.getSourceContainerId()).isNotNull();
                    });
            assertThat(stateMutationRepository.findAll()).hasSize(10);
            assertThat(stateContainerRepository.findAll()).singleElement().satisfies(account -> {
                assertThat(account.getContainerType()).isEqualTo("BANK_ACCOUNT");
                assertThat(account.getCurrentValue()).isEqualByComparingTo("5452");
            });
            assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
                assertThat(session.getActiveIntent()).isNull();
                assertThat(session.getWaitingForField()).isNull();
                assertThat(session.getPendingEvents()).isEmpty();
            });
        });

        System.out.println("==================================================================\n");
    }

    /**
     * LOCKED STRICT ACCEPTANCE CONTRACT — DO NOT MODIFY TO MAKE PRODUCTION CODE PASS.
     *
     * This is the canonical explicit-UPI provisional-account journey. Its exact user messages,
     * provisional account creation, balance follow-up, applied expense, read-only query, mutation
     * count, and final session cleanup jointly define the required behavior.
     * Readable contract: classpath:live-model-transcripts/it_live_003.txt
     *
     * AI AGENTS: the adjacent AGENTS.md governs this method. Do not edit, weaken, disable, reorder,
     * or regenerate it unless the user explicitly requests a change to this exact protected test and
     * then provides two separate confirmations. Fix application code instead.
     */
    @Test
    void it_live_003() throws Exception {
        requireRealApiKey();
        System.out.println("\n================ LIVE MODEL WHATSAPP CONVERSATION ================");
        System.out.println("Model: " + modelName);

        int message = 1;
        String intro = chatText(id(message++), "Hi");
        assertThat(intro)
                .contains("I can help record operational activity through conversation")
                .contains("Describe what happened naturally");

        scenario("Explicit UPI on the first expense creates one provisional bank account");
        String balanceQuestion = chatText(id(message++), "I spent 50 on groceries today from my upi");
        assertThat(balanceQuestion)
                .containsIgnoringCase("created My bank account")
                .containsIgnoringCase("current balance");
        assertWaitingFor("sourceBalance");
        assertExpenseCount(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(stateContainerRepository.findAll()).singleElement().satisfies(account -> {
                    assertThat(account.getName()).isEqualTo("My bank account");
                    assertThat(account.getContainerType()).isEqualTo("BANK_ACCOUNT");
                    assertThat(account.getCurrentValue()).isNull();
                }));

        String setupConfirmation = chatText(id(message++), "10k");
        assertThat(setupConfirmation).contains("50").contains("9950");
        assertExpenseApplied(1, "50");
        assertAccount("My bank account", "BANK_ACCOUNT", "9950", null, null);

        scenario("Read-only total and persisted reconciliation");
        assertThat(chatText(id(message++), "What did I spend today?")).contains("50");
        assertExpenseCount(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(stateMutationRepository.findAll()).hasSize(1);
            assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
                assertThat(session.getActiveIntent()).isNull();
                assertThat(session.getWaitingForField()).isNull();
                assertThat(session.getPendingEvents()).isEmpty();
            });
        });

        System.out.println("==================================================================\n");
    }

    /**
     * Monthly/category insights, persisted budgets, threshold alerts and card-due reminders.
     * Readable contract: classpath:live-model-transcripts/it_live_004.txt
     */
    @Test
    void it_live_004() throws Exception {
        requireRealApiKey();
        System.out.println("\n================ LIVE MODEL MONEY PLAN CONVERSATION ================");
        System.out.println("Model: " + modelName);

        int message = 1;
        scenario("Set up funding and a card with an upcoming recurring due day");
        assertSaved(chatText(id(message++), "Create my HDFC salary bank account with a current balance of 20000"), "HDFC");
        assertSaved(chatText(id(message++), "Create my ICICI Coral credit card with limit 50000, outstanding 2500 and due day 12"), "ICICI Coral");

        scenario("Create an updatable monthly category budget");
        String budgetReply = chatText(id(message++), "Set my monthly groceries budget to 1000");
        assertThat(budgetReply).containsIgnoringCase("Groceries").contains("1000");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(monthlyBudgetRepository.findAll()).singleElement().satisfies(budget -> {
                    assertThat(budget.getCategory()).isEqualToIgnoringCase("Groceries");
                    assertThat(budget.getMonthlyLimit()).isEqualByComparingTo("1000");
                    assertThat(budget.isActive()).isTrue();
                }));

        scenario("Cross warning and overspend thresholds through ordinary expense capture");
        String warning = chatText(id(message++), "I spent 850 on groceries using my HDFC salary bank account");
        assertRecorded(warning, "850");
        assertThat(warning).containsIgnoringCase("Budget alert").containsIgnoringCase("remaining");

        String exceeded = chatText(id(message++), "I spent 300 on groceries using my HDFC salary bank account");
        assertRecorded(exceeded, "300");
        assertThat(exceeded).containsIgnoringCase("Budget alert").containsIgnoringCase("over");

        scenario("Read-only monthly category summary and budget status");
        int mutationsBeforeQueries = stateMutationRepository.findAll().size();
        String monthly = chatText(id(message++), "Give me my spending summary for this month by category");
        assertThat(monthly).contains("1150").containsIgnoringCase("Groceries");
        String budgetStatus = chatText(id(message++), "How am I doing against my budgets?");
        assertThat(budgetStatus).containsIgnoringCase("Groceries").contains("1150").containsIgnoringCase("over budget");

        scenario("Read-only card reminder derived from persisted outstanding and due day");
        String reminders = chatText(id(message++), "Which credit card payments are due soon?");
        assertThat(reminders).containsIgnoringCase("ICICI Coral").contains("2500").contains("12");

        scenario("Reconcile advisory features against the financial ledger");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(stateChangeRepository.findAll()).hasSize(2)
                    .allSatisfy(expense -> assertThat(expense.getTransactionType().name()).isEqualTo("EXPENSE"));
            assertThat(stateMutationRepository.findAll()).hasSize(mutationsBeforeQueries);
            assertThat(monthlyBudgetRepository.findAll()).hasSize(1);
            assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
                assertThat(session.getActiveIntent()).isNull();
                assertThat(session.getWaitingForField()).isNull();
                assertThat(session.getPendingEvents()).isEmpty();
            });
        });
        System.out.println("====================================================================\n");
    }

    private String id(int sequence) {
        return "wamid.live-" + sequence;
    }

    private void assertSaved(String reply, String accountName) {
        assertThat(reply).containsIgnoringCase("created").containsIgnoringCase(accountName);
    }

    private void assertRecorded(String reply, String amount) {
        assertThat(reply)
                .as("The turn must be completed, not left as an unsafe/unsupported interpretation")
                .contains(amount)
                .doesNotContainIgnoringCase("cannot safely")
                .doesNotContainIgnoringCase("not safe")
                .doesNotContainIgnoringCase("could not");
    }

    private void assertLiabilityPayment(String reply, String amount, String cardName, int expectedTransfers) {
        assertThat(reply).contains(amount).containsIgnoringCase(cardName);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var transfers = stateChangeRepository.findAll().stream()
                    .filter(change -> "TRANSFER".equals(change.getTransactionType().name()))
                    .toList();
            assertThat(transfers).hasSize(expectedTransfers);
            Long cardId = stateContainerRepository.findAll().stream()
                    .filter(account -> account.getName().toLowerCase().contains(cardName.toLowerCase()))
                    .findFirst().orElseThrow().getId();
            assertThat(transfers).anySatisfy(transfer -> {
                assertThat(transfer.getAmount()).isEqualByComparingTo(amount);
                assertThat(transfer.getTargetContainerId()).isEqualTo(cardId);
                assertThat(transfer.isFinanciallyApplied()).isTrue();
            });
        });
    }

    private void assertAccount(String nameFragment, String type, String value, String limit, Integer dueDay) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(stateContainerRepository.findAll())
                        .filteredOn(account -> account.getName().toLowerCase().contains(nameFragment.toLowerCase()))
                        .singleElement()
                        .satisfies(account -> {
                            assertThat(account.getContainerType()).isEqualTo(type);
                            assertThat(account.getCurrentValue()).isEqualByComparingTo(value);
                            if (limit != null) assertThat(account.getCapacityLimit()).isEqualByComparingTo(limit);
                            if (dueDay != null) assertThat(account.getDetails()).containsEntry("dueDay", dueDay);
                        }));
    }

    private void assertTwoMonthLedger() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var changes = stateChangeRepository.findAll();
            assertThat(changes).hasSize(12);
            assertThat(changes).filteredOn(change -> "INCOME".equals(change.getTransactionType().name())).hasSize(2);
            assertThat(changes).filteredOn(change -> "EXPENSE".equals(change.getTransactionType().name())).hasSize(6);
            assertThat(changes).filteredOn(change -> "TRANSFER".equals(change.getTransactionType().name())).hasSize(4);
            assertThat(changes).allSatisfy(change -> {
                assertThat(change.isFinanciallyApplied()).isTrue();
                assertThat(change.getTimestamp()).isNotNull();
            });

            // Expense routing is the central regression guard: three cards must never collapse to the first card.
            assertExpenseRoute("12000", "HDFC Millennia");
            assertExpenseRoute("4500", "ICICI Amazon");
            assertExpenseRoute("3000", "SBI SimplyCLICK");
            assertExpenseRoute("6000", "HDFC Millennia");
            assertExpenseRoute("2500", "ICICI Amazon");
            assertExpenseRoute("1500", "SBI SimplyCLICK");

            // 2 income credits + 6 expense debits + (4 payments x debit-and-credit) = 16 mutations.
            assertThat(stateMutationRepository.findAll()).hasSize(16);
        });
    }

    private void printReconciliationSummary() {
        var changes = stateChangeRepository.findAll();
        var mutations = stateMutationRepository.findAll();
        long income = changes.stream().filter(change -> "INCOME".equals(change.getTransactionType().name())).count();
        long expenses = changes.stream().filter(change -> "EXPENSE".equals(change.getTransactionType().name())).count();
        long transfers = changes.stream().filter(change -> "TRANSFER".equals(change.getTransactionType().name())).count();
        long credits = mutations.stream().filter(mutation -> "CREDIT".equals(mutation.getAdjustmentType().name())).count();
        long debits = mutations.stream().filter(mutation -> "DEBIT".equals(mutation.getAdjustmentType().name())).count();
        long payments = mutations.stream().filter(mutation -> "PAYMENT".equals(mutation.getAdjustmentType().name())).count();

        printContainerSnapshot();
        System.out.println("Ledger: " + changes.size() + " state changes"
                + " [income=" + income + ", expense=" + expenses + ", transfer=" + transfers + "]");
        System.out.println("Mutations: " + mutations.size()
                + " [credit=" + credits + ", debit=" + debits + ", payment=" + payments + "]");
        System.out.println("Reconciliation: PASS — persisted containers, state changes, routing, and mutations match the oracle.");
    }

    private void assertExpenseRoute(String amount, String accountName) {
        Long accountId = stateContainerRepository.findAll().stream()
                .filter(account -> account.getName().toLowerCase().contains(accountName.toLowerCase()))
                .findFirst().orElseThrow().getId();
        assertThat(stateChangeRepository.findAll())
                .filteredOn(change -> "EXPENSE".equals(change.getTransactionType().name())
                        && change.getAmount().compareTo(new BigDecimal(amount)) == 0)
                .singleElement()
                .extracting(change -> change.getSourceContainerId())
                .isEqualTo(accountId);
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
        String reply = printReply(awaitNextReply(before));
        printContainerSnapshot();
        return reply;
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

    private void printContainerSnapshot() {
        var accounts = stateContainerRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(account -> account.getContainerType() + account.getName()))
                .toList();
        if (accounts.isEmpty()) return;

        System.out.println("State containers (persisted):");
        accounts.forEach(account -> {
            String valueType = "CREDIT_CARD".equals(account.getContainerType()) ? "outstanding" : "balance";
            String value = account.getCurrentValue() == null
                    ? "unknown" : "₹" + account.getCurrentValue().stripTrailingZeros().toPlainString();
            StringBuilder line = new StringBuilder("  • ")
                    .append(account.getName()).append(" — ").append(valueType).append(" ").append(value);
            if ("CREDIT_CARD".equals(account.getContainerType())) {
                if (account.getCapacityLimit() != null) {
                    line.append(", limit ₹").append(account.getCapacityLimit().stripTrailingZeros().toPlainString());
                }
                if (account.getDetails() != null && account.getDetails().get("dueDay") != null) {
                    line.append(", due day ").append(account.getDetails().get("dueDay"));
                }
            }
            System.out.println(line);
        });
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
