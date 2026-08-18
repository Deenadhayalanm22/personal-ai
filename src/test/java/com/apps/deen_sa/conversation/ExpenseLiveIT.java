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
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import com.apps.deen_sa.finance.budget.MonthlyBudgetRepository;
import com.apps.deen_sa.finance.expense.ExpenseRecordStatus;
import com.apps.deen_sa.conversation.UnprocessedConversationMessageRepository;
import com.apps.deen_sa.finance.legacy.state.cache.StateContainerCache;
import org.flywaydb.core.Flyway;

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
    private String phone = "919876543299";
    private boolean autoAuthorizeFinancialWrites = true;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ConversationSessionRepository sessionRepository;
    @Autowired private MonthlyBudgetRepository monthlyBudgetRepository;
    @Autowired private UnprocessedConversationMessageRepository unprocessedMessageRepository;
    @Autowired private UserFeatureFlagRepository userAccessRepository;
    @Autowired private Flyway flyway;
    @Autowired private StateContainerCache stateContainerCache;
    @Autowired private ConversationContext conversationContext;

    @Value("${wiremock.admin-url}") private String wireMockAdminUrl;

    /** Live WhatsApp contract for super-admin managed user access. */
    @Test
    void it_live_super_admin_adds_and_removes_user_access() throws Exception {
        resetBetweenPersonas();
        autoAuthorizeFinancialWrites = true;
        String superAdmin = "919876543210";
        String managedUser = "919876543215";

        scenario("Super admin grants a normal user access from WhatsApp");
        phone = superAdmin;
        String granted = chatText("wamid.access-add", "add user +" + managedUser);
        assertThat(granted).isEqualTo("Access enabled for +" + managedUser + ".");
        assertThat(userAccessRepository.findByChannelAndExternalUserId("WHATSAPP", managedUser))
                .get().satisfies(access -> {
                    assertThat(access.getRole()).isEqualTo(UserFeatureFlagService.USER);
                    assertThat(access.isEnabled()).isTrue();
                });

        scenario("The newly added normal user can use the application");
        phone = managedUser;
        assertThat(chatText("wamid.access-user", "Hi"))
                .containsIgnoringCase("personal expenses")
                .doesNotContainIgnoringCase("Access is not enabled");

        scenario("Super admin revokes access without deleting the user record");
        phone = superAdmin;
        String removed = chatText("wamid.access-remove", "remove user +" + managedUser);
        assertThat(removed).isEqualTo("Access removed for +" + managedUser + ".");
        assertThat(userAccessRepository.findByChannelAndExternalUserId("WHATSAPP", managedUser))
                .get().satisfies(access -> {
                    assertThat(access.getRole()).isEqualTo(UserFeatureFlagService.USER);
                    assertThat(access.isEnabled()).isFalse();
                });

        scenario("The revoked user is blocked while the access record remains available for reactivation");
        phone = managedUser;
        assertThat(chatText("wamid.access-blocked", "Hi"))
                .isEqualTo("Access is not enabled for this mobile number. Please contact the administrator.");
        assertThat(userAccessRepository.findByChannelAndExternalUserId("WHATSAPP", managedUser)).isPresent();
    }

    /**
     * Focused live-model coverage for manual Discard, Not now, and optional source setup choices.
     * The master contract uses the same preview assertions while confirming its ledger expenses.
     */
    @Test
    void it_live_expense_confirmation_and_optional_source_setup() throws Exception {
        requireRealApiKey();
        resetBetweenPersonas();
        autoAuthorizeFinancialWrites = false;
        int message = 1;

        scenario("AI proposes classification; Discard leaves no financial record");
        String discardedPreview = chatText(id(message++),
                "Weekend dinner with family at BBQ Nation for ₹3,400 paid via HDFC Credit Card.");
        assertThat(discardedPreview)
                .contains("Amount: ₹3400")
                .containsIgnoringCase("Category: Food")
                .containsIgnoringCase("Subcategory: Eating Out")
                .contains("Source: null")
                .containsIgnoringCase("Detected account: HDFC Credit Card")
                .containsIgnoringCase("Confirm this expense");
        assertWaitingFor("EXPENSE", "confirmExpense");
        assertExpenseCount(0);
        assertThat(chatButton(id(message++), "answer:DISCARD_EXPENSE", "Discard"))
                .containsIgnoringCase("nothing was saved");
        assertExpenseCount(0);

        scenario("Confirm saves; unconfigured source setup remains optional");
        String confirmedPreview = chatText(id(message++),
                "Weekend dinner with family at BBQ Nation for ₹3,400 paid via HDFC Credit Card.");
        assertThat(confirmedPreview).contains("Source: null").containsIgnoringCase("Confirm this expense");
        String setupOffer = chatButton(id(message++), "answer:CONFIRM_EXPENSE", "Confirm");
        assertThat(setupOffer)
                .contains("3400")
                .containsIgnoringCase("HDFC Credit Card is not set up")
                .containsIgnoringCase("Set it up");
        assertWaitingFor("EXPENSE", "setupSourceAccount");
        assertExpenseCount(1);
        assertThat(stateContainerRepository.findAll()).isEmpty();

        assertRecorded(chatButton(id(message++), "answer:SKIP_SOURCE_SETUP", "Not now"), "3400");
        assertExpenseCount(1);
        assertThat(stateContainerRepository.findAll()).isEmpty();
        assertThat(stateMutationRepository.findAll()).isEmpty();

        scenario("A later transaction can opt into setup after it is confirmed");
        String secondPreview = chatText(id(message++),
                "Lunch at Pizza Hut for ₹1,200 paid via HDFC Credit Card.");
        assertThat(secondPreview).contains("Source: null").containsIgnoringCase("Confirm this expense");
        assertThat(chatButton(id(message++), "answer:CONFIRM_EXPENSE", "Confirm"))
                .containsIgnoringCase("not set up");
        String balanceQuestion = chatButton(id(message++), "answer:SETUP_SOURCE_ACCOUNT", "Set up account");
        assertThat(balanceQuestion)
                .containsIgnoringCase("HDFC Credit Card")
                .containsIgnoringCase("credit limit");
        assertWaitingFor("EXPENSE", "creditLimit");
        assertExpenseCount(2);
        assertThat(stateContainerRepository.findAll()).singleElement().satisfies(account -> {
            assertThat(account.getName()).isEqualToIgnoringCase("HDFC Credit Card");
            assertThat(account.getCurrentValue()).isNull();
        });
        autoAuthorizeFinancialWrites = true;
    }

    /** Regression coverage derived from the 14–18 August 2026 production usability export. */
    @Test
    void it_live_usage_report_regressions() throws Exception {
        requireRealApiKey();
        resetBetweenPersonas();
        autoAuthorizeFinancialWrites = false;
        phone = "919876543298";

        scenario("Telegram-style start command is treated as help");
        assertThat(chatText("wamid.regression-start", "/start"))
                .containsIgnoringCase("personal expenses");

        scenario("A complete new expense replaces a stale confirmation without saving the stale amount");
        String stalePreview = chatText("wamid.regression-stale",
                "And fish for 300 using bank account");
        assertThat(stalePreview).contains("₹300").containsIgnoringCase("Confirm this expense");
        assertWaitingFor("EXPENSE", "confirmExpense");
        assertThat(stateChangeRepository.findAll()).isEmpty();

        String replacement = chatText("wamid.regression-replacement", "Spend for Amazon grocery 505");
        assertThat(replacement).containsIgnoringCase("How did you pay");
        assertWaitingFor("EXPENSE", "sourceAccount");
        assertThat(sessionRepository.findAll()).singleElement().satisfies(session ->
                assertThat(String.valueOf(session.getPartialJson().get("amount"))).startsWith("505"));
        assertThat(stateChangeRepository.findAll()).isEmpty();

        String replacementPreview = chatButton("wamid.regression-source", "answer:BANK_ACCOUNT", "Bank / UPI");
        assertThat(replacementPreview).contains("₹505").containsIgnoringCase("Confirm this expense");
        String setupOffer = chatButton("wamid.regression-confirm", "answer:CONFIRM_EXPENSE", "Confirm");
        assertThat(setupOffer).contains("505").containsIgnoringCase("not set up");
        assertThat(chatButton("wamid.regression-setup", "answer:SETUP_SOURCE_ACCOUNT", "Set up account"))
                .containsIgnoringCase("current balance");
        assertThat(chatText("wamid.regression-balance", "1000")).contains("495");
        assertThat(stateChangeRepository.findAll()).singleElement()
                .satisfies(expense -> assertThat(expense.getAmount()).isEqualByComparingTo("505"));

        scenario("Named weekday is a date and never part of an account name");
        String saturdayPreview = chatText("wamid.regression-saturday",
                "Add 42.5 for grocery on Saturday from bank account");
        assertThat(saturdayPreview).contains("₹42.5").containsIgnoringCase("My bank account")
                .doesNotContainIgnoringCase("Saturday from bank account");
        assertThat(chatButton("wamid.regression-saturday-confirm", "answer:CONFIRM_EXPENSE", "Confirm"))
                .contains("42.5");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(stateChangeRepository.findAll()).filteredOn(expense ->
                                expense.getAmount().compareTo(new BigDecimal("42.5")) == 0)
                        .singleElement().satisfies(expense -> {
                            var expected = java.time.LocalDate.now(ZoneId.of("Asia/Kolkata"))
                                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(
                                            java.time.DayOfWeek.SATURDAY));
                            assertThat(expense.getTimestamp().atZone(ZoneId.of("UTC")).toLocalDate())
                                    .isEqualTo(expected);
                        }));
        assertThat(stateContainerRepository.findAll())
                .noneSatisfy(account -> assertThat(account.getName()).containsIgnoringCase("Saturday"));

        scenario("Flexible summary and last-transaction wording remain available");
        assertThat(chatText("wamid.regression-summary", "What my spending summary"))
                .contains("547.5");
        assertThat(chatText("wamid.regression-last", "Show my last transaction as I want to edit"))
                .contains("42.5");

        scenario("A family-support payment retains its explicit UPI source on the first turn");
        String parentSupport = chatText("wamid.regression-parent-support",
                "transferred 500 to my father paid using upi");
        assertThat(parentSupport).contains("₹500")
                .containsIgnoringCase("Family Support")
                .containsIgnoringCase("Parents Support")
                .containsIgnoringCase("Source: My bank account")
                .containsIgnoringCase("Confirm this expense")
                .doesNotContainIgnoringCase("How did you pay");

        autoAuthorizeFinancialWrites = true;
    }

    @Test
    void it_live_full_month_scenario() throws Exception {
        requireRealApiKey();
        int message = 1;

        scenario("Day 1 · Set up accounts, receive allowances, transfer money, and configure budgets");
        // Prepaid/capped cards currently use balance-bearing account semantics. This intentionally
        // exercises overdrafts instead of incorrectly treating them as revolving credit cards.
        assertSaved(chatText(id(message++),
                "Create my HDFC bank account with a current balance of 10"), "HDFC");
        assertThat(chatText(id(message++),
                "Setup my HDFC bank account with a current balance of 100"))
                .containsIgnoringCase("already exists")
                .containsIgnoringCase("did not create a duplicate");
        assertSaved(chatText(id(message++), "Create my HDFC Food Card bank account with a current balance of 0"), "Food Card");
        assertSaved(chatText(id(message++), "Create my HDFC Petrol Card bank account with a current balance of 0"), "Petrol Card");
        assertSaved(chatText(id(message++),
                "Create my HDFC credit card with limit 300000, outstanding 0 and due day 21"), "HDFC credit card");

        String salaryAccountQuestion = chatText(id(message++),
                "Salary credited: ₹1,00,000 received today.");
        assertThat(salaryAccountQuestion)
                .containsIgnoringCase("Which bank account")
                .containsIgnoringCase("received this money");
        assertWaitingFor("INCOME", "destinationAccount");
        assertRecorded(chatText(id(message++), "HDFC bank account"), "100000");
        assertRecorded(chatText(id(message++),
                "₹4,000 monthly allowance was credited to my HDFC Food Card bank account."), "4000");
        assertAccount("HDFC Food Card", "BANK_ACCOUNT", "4000", null, null);

        assertRecorded(chatText(id(message++),
                "₹2,500 monthly petrol allowance was credited to my HDFC Petrol Card bank account."), "2500");
        assertAccount("HDFC Petrol Card", "BANK_ACCOUNT", "2500", null, null);
        int expensesBeforeMom = recordedExpenseCount();
        String momReply = chatText(id(message++),
                "Sent ₹10,000 from my HDFC bank account to my mom via UPI.");
        if (momReply.toLowerCase().contains("expense for")) {
            assertWaitingFor("category");
            assertRecorded(chatText(id(message++), "Parents Support"), "10000");
        } else {
            assertRecorded(momReply, "10000");
        }
        assertLatestExpense(expensesBeforeMom, "10000", "Parents Support", "HDFC bank account");

        int expensesBeforeWife = recordedExpenseCount();
        String wifeReply = chatText(id(message++),
                "Sent ₹10,000 from my HDFC bank account to my wife via UPI.");
        if (wifeReply.toLowerCase().contains("expense for")) {
            assertWaitingFor("category");
            assertRecorded(chatText(id(message++), "Family Support"), "10000");
        } else {
            assertRecorded(wifeReply, "10000");
        }
        assertLatestExpense(expensesBeforeWife, "10000", "Family Support", "HDFC bank account");
        assertRecorded(chatText(id(message++),
                "Paid ₹16,000 house rent directly from my HDFC bank account via net banking."), "16000");

        scenario("Days 2–12 · Record ordinary spending before alert thresholds");
        assertRecorded(chatText(id(message++),
                "Ordered quick groceries on Blinkit for ₹420 using my HDFC food card."), "420");
        assertThat(chatText(id(message++), "Setup my grocery budget ₹5,000 for this month."))
                .containsIgnoringCase("Groceries").contains("5000");
        assertRecorded(chatText(id(message++),
                "Bought fresh veggies from the local vendor for ₹180 using my HDFC bank account via UPI."), "180");
        assertRecorded(chatText(id(message++),
                "Paid monthly ACT Fiber net bill of ₹1,000 using my HDFC Credit Card."), "1000");
        assertRecorded(chatText(id(message++),
                "Recharged mobile for ₹799 using HDFC Credit Card."), "799");
        assertRecorded(chatText(id(message++),
                "Filled car petrol for ₹4,000 at Bharat Petroleum using my HDFC Petrol card."), "4000");
        assertRecorded(chatText(id(message++),
                "Swiggy lunch order at office for ₹340 paid using my food card."), "340");
        assertExpenseClassification("340", "Eating Out");
        assertThat(chatText(id(message++), "Setup my eating out budget ₹5,000 for this month."))
                .containsIgnoringCase("Eating Out").contains("5000");
        assertRecorded(chatText(id(message++),
                "Bought daily essentials and soap at the local store for ₹850 using my food card."), "850");
        assertRecorded(chatText(id(message++),
                "Paid electricity bill of ₹2,150 through my HDFC bank account via UPI."), "2150");
        assertRecorded(chatText(id(message++),
                "Weekend dinner with family at BBQ Nation for ₹3,400 paid via HDFC Credit Card."), "3400");
        assertExpenseClassification("3400", "Eating Out");
        assertRecorded(chatText(id(message++),
                "Booked movie tickets on BookMyShow for ₹900 using HDFC Credit Card."), "900");
        int mutationsBeforeBudgetQuery = stateMutationRepository.findAll().size();
        String budgetStatus = chatText(id(message++), "Show my budget status");
        assertThat(budgetStatus).containsIgnoringCase("Eating Out").contains("3740").contains("1260");
        assertThat(stateMutationRepository.findAll()).hasSize(mutationsBeforeBudgetQuery);

        scenario("Days 14–17 · Trigger near-limit and over-budget alerts");
        String shoppingWarning = chatText(id(message++),
                "Ordered clothes online from Zudio via Dunzo delivery for ₹1,800 paid via HDFC Credit Card.");
        assertRecorded(shoppingWarning, "1800");
        assertExpenseClassification("1800", "Clothing");
        assertThat(chatText(id(message++), "Setup my shopping budget ₹2,000 for this month."))
                .containsIgnoringCase("Shopping").contains("2000");

        assertRecorded(chatText(id(message++),
                "Zepto daily milk and snacks order for ₹260 paid using food card."), "260");
        assertRecorded(chatText(id(message++),
                "Refilled bike petrol for ₹600 at HPCL using my HDFC Petrol card."), "600");

        String eatingOutExceeded = chatText(id(message++),
                "Team lunch at Social Bar for ₹1,500 paid via HDFC Credit Card.");
        assertRecorded(eatingOutExceeded, "1500");
        assertExpenseClassification("1500", "Eating Out");
        assertThat(eatingOutExceeded).containsIgnoringCase("Budget alert")
                .containsIgnoringCase("over").contains("240");

        scenario("Days 18–21 · Continue spending and pay the credit-card bill");
        String shoppingExceeded = chatText(id(message++),
                "Bought shoes online on Myntra sale for ₹3,200 using my HDFC Credit Card.");
        assertRecorded(shoppingExceeded, "3200");
        assertThat(shoppingExceeded).containsIgnoringCase("Budget alert")
                .containsIgnoringCase("over").contains("3000");
        assertExpenseClassification("3200", "Clothing");
        assertRecorded(chatText(id(message++),
                "Car minor service and wash at local garage cost ₹2,500, paid using my HDFC bank account via UPI."), "2500");
        assertRecorded(chatText(id(message++),
                "Yearly term insurance premium of ₹50,000 paid using HDFC Credit Card."), "50000");
        assertRecorded(chatText(id(message++),
                "Bought evening snacks and tea at local stall for ₹120 via my HDFC bank account UPI."), "120");
        String paymentPreview = chatText(id(message++),
                "Paid HDFC Credit Card bill amount ₹61,299 directly from my HDFC bank account via Net Banking.");
        assertThat(paymentPreview)
                .contains("Amount: ₹61299")
                .containsIgnoringCase("From: HDFC bank account")
                .containsIgnoringCase("To: HDFC credit card")
                .containsIgnoringCase("Confirm this payment");
        assertWaitingFor("LIABILITY_PAYMENT", "confirmLiabilityPayment");
        assertThat(stateChangeRepository.findAll()).filteredOn(change ->
                "TRANSFER".equals(change.getTransactionType().name())).isEmpty();
        assertLiabilityPayment(chatButton(id(message++),
                        "answer:CONFIRM_LIABILITY_PAYMENT", "Confirm"),
                "61299", "HDFC Credit Card", 1);

        scenario("Days 23–29 · Cross the grocery budget and finish the month");
        assertRecorded(chatText(id(message++),
                "Ordered big grocery restocking on BigBasket for ₹2,100 using food card."), "2100");
        assertRecorded(chatText(id(message++),
                "Filled car petrol again for ₹3,500 using HDFC Petrol card."), "3500");

        String groceriesExceeded = chatText(id(message++),
                "Restocked dry fruits and imported snacks from local gourmet supermarket for ₹1,500 using my HDFC bank account via UPI.");
        assertRecorded(groceriesExceeded, "1500");
        assertThat(groceriesExceeded).containsIgnoringCase("Budget alert")
                .containsIgnoringCase("over").contains("310");

        assertRecorded(chatText(id(message++),
                "Ordered medicine on Tata 1mg for ₹1,200 using HDFC Credit Card."), "1200");
        assertRecorded(chatText(id(message++),
                "Swiggy Instamart ice cream order for ₹310 using food card."), "310");
        assertRecorded(chatText(id(message++),
                "Bike puncture and chain oiling at local shop for ₹150 paid via my HDFC bank account UPI."), "150");

        String finalDinner = chatText(id(message++),
                "Dinner out at local restaurant for ₹1,450 paid using HDFC Credit Card.");
        assertRecorded(finalDinner, "1450");
        assertExpenseClassification("1450", "Eating Out");
        assertThat(finalDinner).containsIgnoringCase("Budget alert")
                .containsIgnoringCase("over").contains("1690");

        assertAccount("HDFC Petrol Card", "BANK_ACCOUNT", "-5600", null, null);
        assertAccount("HDFC Food Card", "BANK_ACCOUNT", "-280", null, null);
        assertAccount("HDFC bank account", "BANK_ACCOUNT", "-3889", null, null);
        assertAccount("HDFC credit card", "CREDIT_CARD", "3950", "300000", 21);
        assertDayOneMonthLedgerReconciled();
    }

    @Test
    void it_live_edit_delete_scenario() throws Exception {
        requireRealApiKey();
        int message = 1;

        scenario("Day 1 · Set up accounts, receive allowances, transfer money, and configure budgets");
        // Prepaid/capped cards currently use balance-bearing account semantics. This intentionally
        // exercises overdrafts instead of incorrectly treating them as revolving credit cards.
        assertSaved(chatText(id(message++),
                "Create my HDFC bank account with a current balance of 10"), "HDFC");
        assertThat(chatText(id(message++),
                "Setup my HDFC bank account with a current balance of 100"))
                .containsIgnoringCase("already exists")
                .containsIgnoringCase("did not create a duplicate");
        assertSaved(chatText(id(message++), "Create my HDFC Food Card bank account with a current balance of 0"), "Food Card");
        assertSaved(chatText(id(message++), "Create my HDFC Petrol Card bank account with a current balance of 0"), "Petrol Card");
        assertSaved(chatText(id(message++),
                "Create my HDFC credit card with limit 300000, outstanding 0 and due day 21"), "HDFC credit card");

        String salaryAccountQuestion = chatText(id(message++),
                "Salary credited: ₹1,00,000 received today.");
        assertThat(salaryAccountQuestion)
                .containsIgnoringCase("Which bank account")
                .containsIgnoringCase("received this money");
        assertWaitingFor("INCOME", "destinationAccount");
        assertRecorded(chatText(id(message++), "HDFC bank account"), "100000");
        assertRecorded(chatText(id(message++),
                "₹4,000 monthly allowance was credited to my HDFC Food Card bank account."), "4000");
        assertAccount("HDFC Food Card", "BANK_ACCOUNT", "4000", null, null);

        assertRecorded(chatText(id(message++),
                "₹2,500 monthly petrol allowance was credited to my HDFC Petrol Card bank account."), "2500");
        assertAccount("HDFC Petrol Card", "BANK_ACCOUNT", "2500", null, null);
        int expensesBeforeMom = recordedExpenseCount();
        String momReply = chatText(id(message++),
                "Sent ₹10,000 from my HDFC bank account to my mom via UPI.");
        if (momReply.toLowerCase().contains("expense for")) {
            assertWaitingFor("category");
            assertRecorded(chatText(id(message++), "Parents Support"), "10000");
        } else {
            assertRecorded(momReply, "10000");
        }
        assertLatestExpense(expensesBeforeMom, "10000", "Parents Support", "HDFC bank account");

        int expensesBeforeWife = recordedExpenseCount();
        String wifeReply = chatText(id(message++),
                "Sent ₹10,000 from my HDFC bank account to my wife via UPI.");
        if (wifeReply.toLowerCase().contains("expense for")) {
            assertWaitingFor("category");
            assertRecorded(chatText(id(message++), "Family Support"), "10000");
        } else {
            assertRecorded(wifeReply, "10000");
        }
        assertLatestExpense(expensesBeforeWife, "10000", "Family Support", "HDFC bank account");
        assertRecorded(chatText(id(message++),
                "Paid ₹16,000 house rent directly from my HDFC bank account via net banking."), "16000");

        scenario("Days 2–12 · Record ordinary spending before alert thresholds");
        assertRecorded(chatText(id(message++),
                "Ordered quick groceries on Blinkit for ₹420 using my HDFC food card."), "420");
        assertThat(chatText(id(message++), "Setup my grocery budget ₹5,000 for this month."))
                .containsIgnoringCase("Groceries").contains("5000");
        assertRecorded(chatText(id(message++),
                "Bought fresh veggies from the local vendor for ₹180 using my HDFC bank account via UPI."), "180");
        assertRecorded(chatText(id(message++),
                "Paid monthly ACT Fiber net bill of ₹1,000 using my HDFC Credit Card."), "1000");
        assertRecorded(chatText(id(message++),
                "Recharged mobile for ₹799 using HDFC Credit Card."), "799");
        assertRecorded(chatText(id(message++),
                "Filled car petrol for ₹4,000 at Bharat Petroleum using my HDFC Petrol card."), "4000");

        scenario("Edit the older Blinkit expense and preserve its accounting history");
        var blinkit = stateChangeRepository.findAll().stream()
                .filter(change -> change.getTransactionType().name().equals("EXPENSE"))
                .filter(change -> change.getAmount().compareTo(new BigDecimal("420")) == 0)
                .filter(change -> change.getMainEntity() != null
                        && change.getMainEntity().toLowerCase().contains("blinkit"))
                .findFirst().orElseThrow();
        int mutationsBeforeEdit = stateMutationRepository.findAll().size();

        String editChoices = chatText(id(message++), "I want to edit a transaction");
        assertThat(editChoices)
                .containsIgnoringCase("Select a transaction")
                .contains("₹4000", "₹799", "₹1000", "₹180", "₹420");
        assertWaitingFor("EXPENSE_CORRECTION", "correctionChoice");

        String editFields = chatButton(id(message++), "answer:SELECT_" + blinkit.getId(), "5");
        assertThat(editFields).containsIgnoringCase("What do you want to change");
        assertWaitingFor("EXPENSE_CORRECTION", "correctionChoice");

        assertThat(chatButton(id(message++), "answer:FIELD_AMOUNT", "Amount"))
                .containsIgnoringCase("correct amount");
        String editPreview = chatText(id(message++), "520");
        assertThat(editPreview)
                .contains("₹420", "₹520")
                .containsIgnoringCase("Confirm this update");

        String edited = chatButton(id(message++), "answer:CONFIRM", "Confirm update");
        assertThat(edited).containsIgnoringCase("updated").contains("₹520");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var original = stateChangeRepository.findById(blinkit.getId()).orElseThrow();
            assertThat(original.getRecordStatus()).isEqualTo(ExpenseRecordStatus.SUPERSEDED);
            assertThat(original.getCorrectionReason()).isEqualTo("USER_EDITED_AMOUNT");
            assertThat(original.getCorrectedAt()).isNotNull();

            assertThat(stateChangeRepository.findAll())
                    .filteredOn(change -> change.getReplacesTransactionId() != null
                            && change.getReplacesTransactionId().equals(blinkit.getId()))
                    .singleElement().satisfies(replacement -> {
                        assertThat(replacement.getRecordStatus()).isEqualTo(ExpenseRecordStatus.ACTIVE);
                        assertThat(replacement.getAmount()).isEqualByComparingTo("520");
                        assertThat(replacement.getRootTransactionId()).isEqualTo(blinkit.getId());
                        assertThat(replacement.getRecordVersion()).isEqualTo(2);
                        assertThat(replacement.isFinanciallyApplied()).isTrue();
                    });
            assertThat(stateMutationRepository.findAll()).hasSize(mutationsBeforeEdit + 2)
                    .anySatisfy(mutation -> {
                        assertThat(mutation.getTransactionId()).isEqualTo(blinkit.getId());
                        assertThat(mutation.getAmount()).isEqualByComparingTo("-420");
                        assertThat(mutation.getReason()).isEqualTo("EXPENSE_CORRECTION_REVERSAL");
                    })
                    .anySatisfy(mutation -> {
                        assertThat(mutation.getAmount()).isEqualByComparingTo("520");
                        assertThat(mutation.getReason()).isEqualTo("EXPENSE_CORRECTION_REPLACEMENT");
                    });
        });
        assertAccount("HDFC Food Card", "BANK_ACCOUNT", "3480", null, null);

        scenario("Delete the mobile recharge by voiding it and reversing its balance impact");
        var mobileRecharge = stateChangeRepository.findAll().stream()
                .filter(change -> change.getTransactionType().name().equals("EXPENSE"))
                .filter(change -> change.getAmount().compareTo(new BigDecimal("799")) == 0)
                .findFirst().orElseThrow();
        int mutationsBeforeDelete = stateMutationRepository.findAll().size();

        String deleteChoices = chatText(id(message++), "I want to delete a transaction");
        assertThat(deleteChoices)
                .containsIgnoringCase("Select a transaction")
                .contains("₹520", "₹4000", "₹799", "₹1000", "₹180");
        String deletePreview = chatButton(id(message++),
                "answer:SELECT_" + mobileRecharge.getId(), "3");
        assertThat(deletePreview)
                .containsIgnoringCase("Delete this transaction")
                .contains("₹799")
                .containsIgnoringCase("voided")
                .containsIgnoringCase("balance impact");

        String deleted = chatButton(id(message++), "answer:CONFIRM", "Delete transaction");
        assertThat(deleted)
                .containsIgnoringCase("Transaction deleted")
                .containsIgnoringCase("voided")
                .contains("₹799");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var voided = stateChangeRepository.findById(mobileRecharge.getId()).orElseThrow();
            assertThat(voided.getRecordStatus()).isEqualTo(ExpenseRecordStatus.VOIDED);
            assertThat(voided.getCorrectionReason()).isEqualTo("USER_DELETED");
            assertThat(voided.getCorrectedAt()).isNotNull();
            assertThat(stateMutationRepository.findAll()).hasSize(mutationsBeforeDelete + 1)
                    .anySatisfy(mutation -> {
                        assertThat(mutation.getTransactionId()).isEqualTo(mobileRecharge.getId());
                        assertThat(mutation.getAmount()).isEqualByComparingTo("-799");
                        assertThat(mutation.getReason()).isEqualTo("EXPENSE_CORRECTION_REVERSAL");
                    });
        });
        assertAccount("HDFC credit card", "CREDIT_CARD", "1000", "300000", 21);

        scenario("Corrected records are the only versions included in budgets and spending reports");
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        Instant dayStart = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant dayEnd = java.time.LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        assertThat(stateChangeRepository.sumExpenseCategory(
                blinkit.getUserId(), "Groceries", dayStart, dayEnd))
                .isEqualByComparingTo("700");
        assertThat(stateChangeRepository.sumExpenses(
                blinkit.getUserId(), dayStart, dayEnd, null, null))
                .isEqualByComparingTo("41700");
        assertThat(stateChangeRepository.findById(blinkit.getId()))
                .get().extracting(change -> change.getRecordStatus()).isEqualTo(ExpenseRecordStatus.SUPERSEDED);
        assertThat(stateChangeRepository.findById(mobileRecharge.getId()))
                .get().extracting(change -> change.getRecordStatus()).isEqualTo(ExpenseRecordStatus.VOIDED);
        assertThat(stateChangeRepository.findAll())
                .filteredOn(change -> change.getTransactionType().name().equals("EXPENSE")
                        && change.getRecordStatus() == ExpenseRecordStatus.ACTIVE)
                .hasSize(7)
                .noneSatisfy(change -> assertThat(change.getAmount()).isEqualByComparingTo("420"))
                .noneSatisfy(change -> assertThat(change.getAmount()).isEqualByComparingTo("799"));
        scenario("Charts · Generate corrected spending images for WhatsApp");
        Path todayChart = chatChart(id(message++),
                "Show my spending summary for today by category", "today-spending.png");
        assertThat(todayChart).isRegularFile();
        assertThat(Files.readAllBytes(todayChart)).startsWith(
                (byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);

        Path monthChart = chatChart(id(message++),
                "Give me this month's spending summary by category", "month-spending.png");
        assertThat(monthChart).isRegularFile();
        assertThat(Files.size(monthChart)).isGreaterThan(10_000);
        System.out.println("Generated chart images:");
        System.out.println("  • " + todayChart.toAbsolutePath());
        System.out.println("  • " + monthChart.toAbsolutePath());
    }


    /**
     * LOCKED STRICT ACCEPTANCE CONTRACT — DO NOT MODIFY TO MAKE PRODUCTION CODE PASS.
     *
     * This is the single canonical four-person finance journey. Its private phases preserve the
     * multi-account ledger, multilingual capture, explicit-UPI first use, planning/alerts, and
     * real-user recovery contracts. Each phase has a clean accounting state; Persona D is reused
     * across two clean-state phases so both planning and provisional-account behavior remain valid.
     * Readable contract: classpath:live-model-transcripts/it_live_complete_unique_finance_contract.txt
     *
     * AI AGENTS: the adjacent AGENTS.md governs this method. Do not edit, weaken, disable, reorder,
     * or regenerate it unless the user explicitly requests a change to this exact protected test and
     * then provides two separate confirmations. Fix application code instead. New scenarios belong in
     * a separate test method unless the confirmed request explicitly changes this contract.
     */
    @Test
    void it_live_complete_unique_finance_contract() throws Exception {
        requireRealApiKey();

        phone = "919876543299";
        personaATwoMonthLedger();

        resetBetweenPersonas();
        phone = "919876543298";
        personaBMultilingualCapture();

        resetBetweenPersonas();
        phone = "919876543297";
        personaCExplicitUpiFirstUse();

        resetBetweenPersonas();
        phone = "919876543296";
        personaDPlanningAndAlerts();

        // The real-user phase intentionally reuses Persona D after a clean reset. Its first UPI
        // expense must still prove provisional-account creation without prior planning accounts.
        resetBetweenPersonas();
        phone = "919876543296";
        personaDRealUserRecovery();
    }

    @Value("${openai.model}") private String modelName;

    private void personaATwoMonthLedger() throws Exception {
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
        assertPaymentPreview(chatText(id(message++), "On 2 July 2026 pay the full HDFC Millennia card bill of 12000 from my HDFC salary account"),
                "12000", "HDFC Millennia");
        assertLiabilityPayment(chatButton(id(message++), "answer:CONFIRM_LIABILITY_PAYMENT", "Confirm"),
                "12000", "HDFC Millennia", 1);
        assertPaymentPreview(chatText(id(message++), "On 10 July 2026 pay my ICICI Amazon Pay card bill of 4500 from my HDFC salary account"),
                "4500", "ICICI Amazon");
        assertLiabilityPayment(chatButton(id(message++), "answer:CONFIRM_LIABILITY_PAYMENT", "Confirm"),
                "4500", "ICICI Amazon", 2);
        assertPaymentPreview(chatText(id(message++), "On 18 July 2026 pay my SBI SimplyCLICK card bill of 3000 from my HDFC salary account"),
                "3000", "SBI SimplyCLICK");
        assertLiabilityPayment(chatButton(id(message++), "answer:CONFIRM_LIABILITY_PAYMENT", "Confirm"),
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

        scenario("Read-only · generic balance query");
        int changesBeforeCardBalanceQuery = stateChangeRepository.findAll().size();
        int mutationsBeforeCardBalanceQuery = stateMutationRepository.findAll().size();
        String iciciBalance = chatText(id(message++), "What is my ICICI Amazon Pay credit card balance?");
        assertThat(iciciBalance)
                .containsIgnoringCase("ICICI Amazon Pay")
                .contains("2500");
        assertAccount("ICICI Amazon", "CREDIT_CARD", "2500", "150000", 12);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(stateChangeRepository.findAll()).hasSize(changesBeforeCardBalanceQuery);
            assertThat(stateMutationRepository.findAll()).hasSize(mutationsBeforeCardBalanceQuery);
        });
        int changesBeforeGenericBalanceQuery = stateChangeRepository.findAll().size();
        int mutationsBeforeGenericBalanceQuery = stateMutationRepository.findAll().size();
        String genericBalance = chatText(id(message++), "What is my balance?");
        assertThat(genericBalance)
                .containsIgnoringCase("HDFC salary bank account").contains("160500")
                .containsIgnoringCase("HDFC Millennia credit card").contains("6000")
                .containsIgnoringCase("ICICI Amazon Pay credit card").contains("2500")
                .containsIgnoringCase("SBI SimplyCLICK credit card").contains("1500");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(stateChangeRepository.findAll()).hasSize(changesBeforeGenericBalanceQuery);
            assertThat(stateMutationRepository.findAll()).hasSize(mutationsBeforeGenericBalanceQuery);
        });

        long transfersBeforeEarlySettlement = transferCount();
        assertPaymentPreview(chatText(id(message++), "On 30 July 2026 pay the full HDFC Millennia card bill of 6000 from my HDFC salary account"),
                "6000", "HDFC Millennia");
        assertThat(transferCount()).isEqualTo(transfersBeforeEarlySettlement);
        assertLiabilityPayment(chatButton(id(message++), "answer:CONFIRM_LIABILITY_PAYMENT", "Confirm"),
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
    private void personaBMultilingualCapture() throws Exception {
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
        assertExpenseCount(0);

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

        scenario("English · natural current-balance query is read-only");
        String balanceSummary = chatText(id(message++), "what is my current balance in my bank");
        assertThat(balanceSummary).contains("7592");
        assertExpenseCount(3);
        assertAccount("My bank account", "BANK_ACCOUNT", "7592", null, null);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
                    assertThat(session.getActiveIntent()).isNull();
                    assertThat(session.getWaitingForField()).isNull();
                    assertThat(session.getPendingEvents()).isEmpty();
                }));

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

        scenario("English · two expenses are independently previewed and confirmed");
        assertRecorded(chatText(id(message++), "Spent 80 on tea using UPI"), "80");
        assertRecorded(chatText(id(message++), "Spent 120 on auto using UPI"), "120");
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
    private void personaCExplicitUpiFirstUse() throws Exception {
        requireRealApiKey();
        System.out.println("\n================ LIVE MODEL WHATSAPP CONVERSATION ================");
        System.out.println("Model: " + modelName);

        int message = 1;
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
    private void personaDPlanningAndAlerts() throws Exception {
        requireRealApiKey();
        System.out.println("\n================ LIVE MODEL MONEY PLAN CONVERSATION ================");
        System.out.println("Model: " + modelName);

        int message = 1;
        scenario("Set up funding and a card with an upcoming recurring due day");
        assertSaved(chatText(id(message++), "Create my HDFC salary bank account with a current balance of 20000"), "HDFC");
        assertSaved(chatText(id(message++), "Create my ICICI Coral credit card with limit 50000, outstanding 2500 and due day 12"), "ICICI Coral");

        scenario("Establish a user-owned grocery scope before budgeting");
        String warning = chatText(id(message++), "I spent 850 on groceries using my HDFC salary bank account");
        assertRecorded(warning, "850");

        scenario("Create an updatable monthly category budget from confirmed expense data");
        String budgetReply = chatText(id(message++), "Set my monthly groceries budget to 1000");
        assertThat(budgetReply).containsIgnoringCase("Groceries").contains("1000");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(monthlyBudgetRepository.findAll()).singleElement().satisfies(budget -> {
                    assertThat(budget.getCategory()).isEqualToIgnoringCase("Groceries");
                    assertThat(budget.getMonthlyLimit()).isEqualByComparingTo("1000");
                    assertThat(budget.isActive()).isTrue();
                }));

        scenario("Cross the overspend threshold through ordinary expense capture");
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

    /**
     * Real-user expense journey covering extension help, AI-backed taxonomy resolution, budgets,
     * and persistence of unsupported demand. Readable contract: live-model-transcripts/it_live_005.txt
     */
    private void personaDRealUserRecovery() throws Exception {
        requireRealApiKey();
        System.out.println("\n================ LIVE MODEL REAL EXPENSE CONVERSATION ================");
        System.out.println("Model: " + modelName);

        int message = 1;
        scenario("Expense-specific greeting and help");
        String greeting = chatText(id(message++), "Hi");
        assertThat(greeting)
                .containsIgnoringCase("personal expenses")
                .containsIgnoringCase("Record expenses and income")
                .containsIgnoringCase("monthly category budgets");
        String help = chatText(id(message++), "Help");
        assertThat(help).isEqualTo(greeting);

        scenario("First UPI expense creates and completes the provisional bank account");
        String balanceQuestion = chatText(id(message++),
                "i spent 55 today morning for some snacks and i paid using upi");
        assertThat(balanceQuestion).containsIgnoringCase("created My bank account")
                .containsIgnoringCase("current balance");
        assertWaitingFor("sourceBalance");
        String balanceConfirmation = chatText(id(message++), "10000");
        assertThat(balanceConfirmation).contains("55").contains("9945");

        scenario("Misspelled free text is semantically mapped by the model to the configured taxonomy");
        String vegetables = chatText(id(message++), "i spent 1300 on the buying vegitables using my upi");
        assertRecorded(vegetables, "1300");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(stateChangeRepository.findAll())
                        .filteredOn(value -> value.getAmount().compareTo(new BigDecimal("1300")) == 0)
                        .singleElement().satisfies(value -> {
                            assertThat(value.getCategory()).isEqualTo("Food & Dining");
                            assertThat(value.getSubcategory()).isEqualTo("Groceries");
                        }));

        scenario("Natural budget wording is linked to the user's confirmed grocery scope");
        String budget = chatText(id(message++), "my grocery balance for this month is only 2000.");
        assertThat(budget).containsIgnoringCase("Groceries").contains("2000");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(monthlyBudgetRepository.findAll()).singleElement().satisfies(value -> {
                    assertThat(value.getCategory()).isEqualTo("Groceries");
                    assertThat(value.getMonthlyLimit()).isEqualByComparingTo("2000");
                }));

        String initialBudgetStatus = chatText(id(message++), "how much my grocery budget for this month");
        assertThat(initialBudgetStatus).contains("Groceries").contains("2000").contains("700")
                .containsIgnoringCase("remaining");

        String plannedStatus = chatText(id(message++),
                "how am i doing my grocery budget against this month planned");
        assertThat(plannedStatus).contains("1300").contains("2000").contains("700")
                .containsIgnoringCase("remaining");

        scenario("Crossing the canonical budget creates an over-budget alert");
        String overBudget = chatText(id(message++), "i spent 800 on groceries today paid using upi");
        assertRecorded(overBudget, "800");
        assertThat(overBudget).containsIgnoringCase("Budget alert").containsIgnoringCase("over").contains("100");

        String today = chatText(id(message++), "how much i spent today");
        assertThat(today).contains("2155").containsIgnoringCase("Groceries");

        scenario("Unsupported demand receives an honest response and enters the review queue");
        String unsupportedText = "Purple silence sideways banana orbit";
        String unsupportedMessageId = id(message++);
        String unsupported = chatText(unsupportedMessageId, unsupportedText);
        assertThat(unsupported).containsIgnoringCase("couldn't understand")
                .containsIgnoringCase("recorded this message").containsIgnoringCase("Help");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(unprocessedMessageRepository.findAll()).singleElement().satisfies(value -> {
                    assertThat(value.getMessageText()).isEqualTo(unsupportedText);
                    assertThat(value.getChannel()).isEqualTo("WHATSAPP");
                    assertThat(value.getStatus()).isEqualTo("NEW");
                    assertThat(value.getReason()).isIn("AMBIGUOUS_OR_UNSUPPORTED", "UNKNOWN_COMMAND");
                    assertThat(value.getExternalMessageId()).isEqualTo(unsupportedMessageId);
                }));

        scenario("Help remains available after an unsupported turn");
        assertThat(chatText(id(message++), "help")).isEqualTo(greeting);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(stateChangeRepository.findAll()).hasSize(3);
            assertThat(stateMutationRepository.findAll()).hasSize(3);
            assertThat(monthlyBudgetRepository.findAll()).hasSize(1);
            assertThat(unprocessedMessageRepository.findAll()).hasSize(1);
        });
        System.out.println("======================================================================\n");
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
                .doesNotContainIgnoringCase("what was the")
                .doesNotContainIgnoringCase("which account")
                .doesNotContainIgnoringCase("which bank account")
                .doesNotContainIgnoringCase("how did you pay")
                .doesNotContainIgnoringCase("please provide")
                .doesNotContainIgnoringCase("cannot safely")
                .doesNotContainIgnoringCase("not safe")
                .doesNotContainIgnoringCase("could not");
    }

    private int recordedExpenseCount() {
        return (int) stateChangeRepository.findAll().stream()
                .filter(change -> "EXPENSE".equals(change.getTransactionType().name()))
                .count();
    }

    private void assertLatestExpense(int previousCount, String amount, String classification,
                                     String sourceAccountName) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var expenses = stateChangeRepository.findAll().stream()
                    .filter(change -> "EXPENSE".equals(change.getTransactionType().name()))
                    .sorted(Comparator.comparingLong(change -> change.getId()))
                    .toList();
            assertThat(expenses).hasSize(previousCount + 1);
            var expense = expenses.getLast();
            assertThat(expense.getAmount()).isEqualByComparingTo(amount);
            assertThat(java.util.stream.Stream.of(expense.getCategory(), expense.getSubcategory())
                    .filter(java.util.Objects::nonNull).toList()).contains(classification);
            assertThat(expense.isFinanciallyApplied()).isTrue();
            Long expectedSourceId = stateContainerRepository.findAll().stream()
                    .filter(account -> account.getName().equalsIgnoreCase(sourceAccountName))
                    .findFirst().orElseThrow().getId();
            assertThat(expense.getSourceContainerId()).isEqualTo(expectedSourceId);
        });
    }

    private void assertExpenseClassification(String amount, String classification) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(stateChangeRepository.findAll()).filteredOn(change ->
                                "EXPENSE".equals(change.getTransactionType().name())
                                        && change.getAmount().compareTo(new BigDecimal(amount)) == 0)
                        .singleElement().satisfies(expense ->
                                assertThat(java.util.stream.Stream.of(expense.getCategory(), expense.getSubcategory())
                                        .filter(java.util.Objects::nonNull).toList()).contains(classification)));
    }

    private void assertDayOneMonthLedgerReconciled() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var changes = stateChangeRepository.findAll();
            assertThat(changes).hasSize(32);
            assertThat(changes).filteredOn(change -> "INCOME".equals(change.getTransactionType().name())).hasSize(3);
            assertThat(changes).filteredOn(change -> "EXPENSE".equals(change.getTransactionType().name())).hasSize(28);
            assertThat(changes).filteredOn(change -> "TRANSFER".equals(change.getTransactionType().name())).hasSize(1);
            assertThat(changes).allSatisfy(change -> assertThat(change.isFinanciallyApplied()).isTrue());
            assertThat(stateMutationRepository.findAll()).hasSize(33);
            assertThat(monthlyBudgetRepository.findAll()).hasSize(3);
            assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
                assertThat(session.getActiveIntent()).isNull();
                assertThat(session.getWaitingForField()).isNull();
                assertThat(session.getPendingEvents()).isEmpty();
            });
        });
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

    private void assertPaymentPreview(String reply, String amount, String cardName) {
        assertThat(reply)
                .contains("Amount: ₹" + amount)
                .containsIgnoringCase("From: HDFC salary bank account")
                .containsIgnoringCase("To: " + cardName)
                .containsIgnoringCase("Confirm this payment");
        assertWaitingFor("LIABILITY_PAYMENT", "confirmLiabilityPayment");
    }

    private long transferCount() {
        return stateChangeRepository.findAll().stream()
                .filter(change -> "TRANSFER".equals(change.getTransactionType().name()))
                .count();
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

    private void resetBetweenPersonas() {
        flyway.clean();
        flyway.migrate();
        stateContainerCache.evictAll();
        conversationContext.reset();
    }

    private void assertWaitingFor(String field) {
        assertWaitingFor("EXPENSE", field);
    }

    private void assertWaitingFor(String intent, String field) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
                    assertThat(session.getActiveIntent()).isEqualTo(intent);
                    assertThat(session.getWaitingForField()).isEqualTo(field);
                    assertThat(session.getLastQuestion()).isNotBlank();
                }));
    }

    private String chatText(String messageId, String userText) throws Exception {
        int before = outgoingMessages().size();
        int expensesBefore = recordedExpenseCount();
        int budgetsBefore = monthlyBudgetRepository.findAll().size();
        System.out.println("Deena: " + userText);
        sendText(messageId, userText);
        String reply = printReply(awaitNextReply(before));
        reply = authorizeExpenseIfPreviewed(messageId, reply, expensesBefore);
        reply = authorizeBudgetIfPreviewed(messageId, reply, budgetsBefore);
        printContainerSnapshot();
        return reply;
    }

    private Path chatChart(String messageId, String userText, String filename) throws Exception {
        int imageMessagesBefore = outgoingImageMessages().size();
        int uploadsBefore = outgoingMediaUploads().size();
        System.out.println("Deena: " + userText);
        sendText(messageId, userText);

        ImageMessage image = awaitNextImageMessage(imageMessagesBefore);
        assertThat(image.caption()).contains("₹").containsIgnoringCase("Category breakdown");
        byte[] png = awaitNextPngUpload(uploadsBefore);
        Path output = Path.of("target", "live-charts", filename);
        Files.createDirectories(output.getParent());
        Files.write(output, png);
        System.out.println("App: " + image.caption() + " [image saved to " + output.toAbsolutePath() + "]");
        return output;
    }

    private String chatButton(String messageId, String buttonId, String title) throws Exception {
        int before = outgoingMessages().size();
        int expensesBefore = recordedExpenseCount();
        int budgetsBefore = monthlyBudgetRepository.findAll().size();
        System.out.println("Deena: " + title + "  [button]");
        sendButton(messageId, buttonId, title);
        String reply = printReply(awaitNextReply(before));
        reply = authorizeExpenseIfPreviewed(messageId, reply, expensesBefore);
        return authorizeBudgetIfPreviewed(messageId, reply, budgetsBefore);
    }

    private String authorizeExpenseIfPreviewed(String messageId, String reply, int expensesBefore) throws Exception {
        if (!autoAuthorizeFinancialWrites || !reply.contains("Confirm this expense?")) return reply;

        assertThat(reply)
                .contains("Amount: ₹")
                .contains("Category:")
                .contains("Subcategory:")
                .contains("Source:");
        assertWaitingFor("EXPENSE", "confirmExpense");
        assertThat(recordedExpenseCount())
                .as("An expense preview must not persist before Confirm")
                .isEqualTo(expensesBefore);

        String confirmed = rawChatButton(messageId + "-confirm", "answer:CONFIRM_EXPENSE", "Confirm");
        if (!confirmed.contains("Set it up for balance and spending insights?")) return confirmed;
        return rawChatButton(messageId + "-setup", "answer:SETUP_SOURCE_ACCOUNT", "Set up account");
    }

    private String authorizeBudgetIfPreviewed(String messageId, String reply, int budgetsBefore) throws Exception {
        if (!autoAuthorizeFinancialWrites || !reply.contains("Confirm this budget?")) return reply;

        assertThat(reply)
                .contains("Monthly budget: ₹")
                .contains("Category:")
                .contains("Subcategory:")
                .contains("Budget scope:");
        assertWaitingFor("BUDGET_SET", "confirmBudget");
        assertThat(monthlyBudgetRepository.findAll())
                .as("A budget preview must not persist before Confirm")
                .hasSize(budgetsBefore);
        return rawChatButton(messageId + "-confirm-budget", "answer:CONFIRM_BUDGET", "Confirm");
    }

    private String rawChatButton(String messageId, String buttonId, String title) throws Exception {
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
        if (!accounts.isEmpty()) {
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

        var budgets = monthlyBudgetRepository.findAll().stream()
                .sorted(Comparator.comparing(budget -> budget.getCategory().toLowerCase()))
                .toList();
        if (budgets.isEmpty()) return;
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        YearMonth month = YearMonth.now(zone);
        Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        System.out.println("Monthly budgets (persisted, " + month + "):");
        budgets.forEach(budget -> {
            BigDecimal spent = stateChangeRepository.sumExpenseCategory(
                    String.valueOf(budget.getUserId()), budget.getCategory(), start, end);
            if (spent == null) spent = BigDecimal.ZERO;
            BigDecimal remaining = budget.getMonthlyLimit().subtract(spent);
            String position = remaining.signum() >= 0
                    ? "remaining ₹" + remaining.stripTrailingZeros().toPlainString()
                    : "over ₹" + remaining.abs().stripTrailingZeros().toPlainString();
            System.out.println("  • " + budget.getCategory()
                    + " — spent ₹" + spent.stripTrailingZeros().toPlainString()
                    + " / limit ₹" + budget.getMonthlyLimit().stripTrailingZeros().toPlainString()
                    + ", " + position + ", active=" + budget.isActive());
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
                """.formatted(messageId, phone, escape(text)));
    }

    private void sendButton(String messageId, String buttonId, String title) throws Exception {
        postWebhook("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "id":"%s","from":"%s","type":"interactive",
                  "interactive":{"type":"button_reply","button_reply":{"id":"%s","title":"%s"}}
                }]}}]}]}
                """.formatted(messageId, phone, escape(buttonId), escape(title)));
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

    private ImageMessage awaitNextImageMessage(int previousCount) {
        ImageMessage[] result = new ImageMessage[1];
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(300)).untilAsserted(() -> {
            List<ImageMessage> images = outgoingImageMessages();
            assertThat(images).hasSizeGreaterThan(previousCount);
            result[0] = images.getLast();
            assertThat(result[0].mediaId()).isNotBlank();
        });
        return result[0];
    }

    private byte[] awaitNextPngUpload(int previousCount) {
        byte[][] result = new byte[1][];
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(300)).untilAsserted(() -> {
            List<byte[]> uploads = outgoingMediaUploads();
            assertThat(uploads).hasSizeGreaterThan(previousCount);
            result[0] = extractPng(uploads.getLast());
            assertThat(result[0]).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        });
        return result[0];
    }

    private List<ImageMessage> outgoingImageMessages() {
        try {
            JsonNode root = wireMockRequests();
            List<ImageMessage> messages = new ArrayList<>();
            for (JsonNode entry : root.path("requests")) {
                JsonNode request = entry.path("request");
                if (!request.path("url").asText().endsWith("/messages")) continue;
                JsonNode payload = objectMapper.readTree(request.path("body").asText());
                if (!"image".equals(payload.path("type").asText())) continue;
                messages.add(new ImageMessage(request.path("loggedDate").asLong(),
                        payload.path("image").path("id").asText(),
                        payload.path("image").path("caption").asText()));
            }
            messages.sort(Comparator.comparingLong(ImageMessage::loggedAt));
            return messages;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read outgoing WhatsApp images", exception);
        }
    }

    private List<byte[]> outgoingMediaUploads() {
        JsonNode root = wireMockRequests();
        List<Map.Entry<Long, byte[]>> uploads = new ArrayList<>();
        for (JsonNode entry : root.path("requests")) {
            JsonNode request = entry.path("request");
            if (!request.path("url").asText().endsWith("/media")) continue;
            uploads.add(Map.entry(request.path("loggedDate").asLong(),
                    Base64.getDecoder().decode(request.path("bodyAsBase64").asText())));
        }
        uploads.sort(Map.Entry.comparingByKey());
        return uploads.stream().map(Map.Entry::getValue).toList();
    }

    private JsonNode wireMockRequests() {
        try {
            return objectMapper.readTree(RestClient.create(wireMockAdminUrl).get().uri("/requests")
                    .retrieve().body(String.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read WireMock requests", exception);
        }
    }

    private byte[] extractPng(byte[] multipartBody) {
        byte[] startMarker = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        byte[] endMarker = {0x49, 0x45, 0x4e, 0x44, (byte) 0xae, 0x42, 0x60, (byte) 0x82};
        int start = indexOf(multipartBody, startMarker, 0);
        int end = indexOf(multipartBody, endMarker, Math.max(0, start));
        if (start < 0 || end < 0) throw new IllegalStateException("WhatsApp upload did not contain a complete PNG");
        return Arrays.copyOfRange(multipartBody, start, end + endMarker.length);
    }

    private int indexOf(byte[] value, byte[] marker, int from) {
        outer: for (int i = from; i <= value.length - marker.length; i++) {
            for (int j = 0; j < marker.length; j++) if (value[i + j] != marker[j]) continue outer;
            return i;
        }
        return -1;
    }

    private record OutgoingMessage(long loggedAt, String text) { }
    private record ImageMessage(long loggedAt, String mediaId, String caption) { }
}
