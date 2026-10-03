package com.apps.deen_sa.insights;

import com.apps.deen_sa.entity.AppUserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Uses a unique schema, never cleans an existing application schema. */
@EnabledIfEnvironmentVariable(named = "EXPENSE_CHAT_TEST_DB_URL", matches = ".+")
class ExpenseQueryPostgresTest {
    static JdbcTemplate jdbc;
    static ExpenseQueryTool tool;
    static FinancialRecordsTool records;
    static long ownerLoanId, otherLoanId, ownerFundId, ownerStockId, ownerCommitmentId, ownerSavingsId;
    static String schema;
    static AppUserEntity owner;
    @BeforeAll static void setup() {
        schema = "chat_test_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("EXPENSE_CHAT_TEST_DB_URL");
        var admin = new DriverManagerDataSource(url, System.getenv("EXPENSE_CHAT_TEST_DB_USER"), System.getenv("EXPENSE_CHAT_TEST_DB_PASSWORD"));
        Flyway.configure().dataSource(admin).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        var dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                System.getenv("EXPENSE_CHAT_TEST_DB_USER"), System.getenv("EXPENSE_CHAT_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource); tool = new ExpenseQueryTool(dataSource); records = new FinancialRecordsTool(dataSource);
        owner = new AppUserEntity(); owner.setId(user("owner"));
        long other = user("other");
        expense(owner.getId(), "2026-09-01", "Food", "250", false);
        expense(owner.getId(), "2026-09-05", "Food", "500", false);
        expense(owner.getId(), "2026-09-06", "Rent", "2000", false);
        expense(owner.getId(), "2026-09-06", "Hidden", "9000", true);
        expense(owner.getId(), "2026-08-06", "Food", "100", false);
        expense(other, "2026-09-05", "Other user", "99000", false);
        ownerLoanId = loan(owner.getId(), "Home EMI"); otherLoanId = loan(other, "Secret loan");
        ownerFundId = investment(owner.getId(), "MUTUAL_FUND", "Index fund", "MF1");
        ownerStockId = investment(owner.getId(), "STOCK", "Acme stock", "ST1");
        investment(other, "MUTUAL_FUND", "Secret fund", "MF2");
        jdbc.update("INSERT INTO investment_transaction(user_investment_id,transaction_kind,status,transaction_date,amount,units,calculation_source) VALUES (?, 'OPENING_BALANCE','CONFIRMED',date '2026-09-01', 1000, 10, 'USER_ENTERED')", ownerFundId);
        jdbc.update("INSERT INTO investment_transaction(user_investment_id,transaction_kind,status,scheduled_month,amount) VALUES (?, 'SIP','SCHEDULED',date '2026-10-01', 500)", ownerFundId);
        jdbc.update("INSERT INTO loan_emi_occurrence(loan_id,due_month,due_date,status,planned_amount) VALUES (?,date '2026-10-01',date '2026-10-05','UPCOMING',30000)", ownerLoanId);
        ownerCommitmentId = jdbc.queryForObject("INSERT INTO user_recurring_commitment(user_id,label,amount_mode,planning_amount,effective_month,status,created_at,updated_at) VALUES (?,?, 'FIXED',25000,date '2026-09-01','ACTIVE',now(),now()) RETURNING id", Long.class, owner.getId(), "Rent");
        ownerSavingsId = jdbc.queryForObject("INSERT INTO commitment_savings_plan(commitment_id,target_date,target_amount,start_month,monthly_amount,final_amount,used_amount,created_at) VALUES (?,date '2027-09-01',20000,date '2026-10-01',2000,2000,0,now()) RETURNING id", Long.class, ownerCommitmentId);
        long account = jdbc.queryForObject("INSERT INTO user_reference_entity(user_id,entity_type,canonical_name) VALUES (?, 'ACCOUNT', 'Primary account') RETURNING id", Long.class, owner.getId());
        jdbc.update("INSERT INTO user_credit_card(user_id,account_reference_id,card_name,issuer_name,statement_day,due_day,start_month) VALUES (?,?,'Visa card','Example Bank',5,20,date '2026-10-01')", owner.getId(), account);

    }
    @AfterAll static void cleanup() { if (jdbc != null) jdbc.execute("DROP SCHEMA " + schema + " CASCADE"); }
    static long user(String name) {
        return jdbc.queryForObject("INSERT INTO app_user(channel,external_user_id) VALUES ('WHATSAPP',?) RETURNING id", Long.class, name);
    }
    static void expense(long user, String date, String category, String amount, boolean deleted) {
        long draft = jdbc.queryForObject("INSERT INTO transaction_draft(user_id,input_type,source,source_message_id,status) VALUES (?, 'TEXT','WHATSAPP',?,'CONSUMED') RETURNING id", Long.class, user, UUID.randomUUID().toString());
        jdbc.update("INSERT INTO financial_transaction(user_id,source_draft_id,occurred_at,category,amount,deleted_at) VALUES (?,?,cast(? as date),?,?,CASE WHEN ? THEN now() ELSE NULL END)", user, draft, date, category, new BigDecimal(amount), deleted);
    }
    static long loan(long user, String name) {
        return jdbc.queryForObject("INSERT INTO user_loan(user_id,loan_name,loan_type,lender_name,original_principal,monthly_emi_amount,total_tenure_months,first_emi_due_date) VALUES (?,?,'HOME','Example Lender',300000,30000,12,date '2026-09-05') RETURNING id", Long.class, user, name);
    }
    static long investment(long user, String type, String name, String instrument) {
        return jdbc.queryForObject("INSERT INTO user_investment(user_id,asset_type,provider,external_instrument_id,display_name_snapshot) VALUES (?,?, 'TEST',?,?) RETURNING id", Long.class, user, type, instrument, name);
    }
    FinancialRecordsTool.Request read(String module, String view, Long id) {
        return new FinancialRecordsTool.Request(module, view, id, "", 20, 0);
    }
    @Test void eachModuleReadsOnlyOwnedRecordsAndHistories() {
        assertThat(records.read(owner, read("loans", "records", null)).rows()).extracting(row -> row.get("name")).containsExactly("Home EMI");
        assertThat(records.read(owner, read("loans", "history", ownerLoanId)).rows()).hasSize(1);
        assertThat(records.read(owner, read("loans", "history", otherLoanId)).rows()).isEmpty();
        assertThat(records.read(owner, read("mutual_funds", "records", null)).rows()).extracting(row -> row.get("name")).containsExactly("Index fund");
        assertThat(records.read(owner, read("mutual_funds", "history", ownerFundId)).rows()).hasSize(2);
        assertThat(records.read(owner, read("stocks", "records", null)).rows()).extracting(row -> row.get("name")).containsExactly("Acme stock");
        assertThat(records.read(owner, read("commitments", "records", null)).rows()).hasSize(1);
        assertThat(records.read(owner, read("savings", "records", null)).rows()).hasSize(1);
        assertThat(records.read(owner, read("credit_cards", "records", null)).rows()).hasSize(1);
        assertThat(records.read(owner,read("credit_cards","records",null)).rows().getFirst()).containsEntry("start_month","2026-10");
        assertThat(records.read(owner,read("accounts","records",null)).rows().getFirst()).containsEntry("account_type","CREDIT_CARD").doesNotContainKeys("balance","trackedBalance","moneyReceived");
        assertThat(records.read(owner, read("accounts", "records", null)).rows()).hasSize(1);
    }
    @Test void confirmedHoldingsExcludeScheduledAmountsAndPagingIsExplicit() {
        var funds = records.read(owner, new FinancialRecordsTool.Request("mutual_funds", "records", null, "index", 1, 0));
        assertThat(funds.rows().getFirst().get("invested_amount")).isEqualTo(new BigDecimal("1000.00"));
        assertThat(funds.rows().getFirst().get("units")).isEqualTo(new BigDecimal("10.000000"));
        assertThat(records.read(owner, new FinancialRecordsTool.Request("loans", "records", null, "", 1, 0)).truncated()).isFalse();
        assertThat(records.read(owner, read("credit_cards", "history", Long.MAX_VALUE)).rows()).isEmpty();
    }
    ExpenseQueryTool.Query query(String mode, List<String> groups, List<ExpenseQueryTool.Filter> filters, int limit) {
        return new ExpenseQueryTool.Query(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-01"), mode, groups, filters, "amount_desc", limit);
    }
    @Test void aggregatesOnlyOwnedVisiblePeriodAndPreservesTotalsWhenTruncated() {
        var result = tool.execute(owner, query("summary", List.of("category"), List.of(), 1));
        assertThat(result.matchingTotal()).isEqualByComparingTo("2750");
        assertThat(result.matchingCount()).isEqualTo(3);
        assertThat(result.truncated()).isTrue();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().get("category")).isEqualTo("Rent");
    }
    @Test void combinesFiltersAndGroupingForAnUnscriptedQuestion() {
        var result = tool.execute(owner, query("summary", List.of("category", "weekday"), List.of(
                new ExpenseQueryTool.Filter("category", "ne", "rent"), new ExpenseQueryTool.Filter("amount", "gte", "300")), 20));
        assertThat(result.matchingTotal()).isEqualByComparingTo("500");
        assertThat(result.rows().getFirst()).containsEntry("weekday", "6");
    }
    @Test void detailLimitDoesNotChangeTotalAndEmptyQueryHasNoInventedRows() {
        var result = tool.execute(owner, query("details", List.of(), List.of(), 2));
        assertThat(result.rows()).hasSize(2);
        assertThat(result.matchingTotal()).isEqualByComparingTo("2750");
        var empty = tool.execute(owner, query("summary", List.of("category"), List.of(new ExpenseQueryTool.Filter("category", "contains", "%' OR 1=1 --")), 20));
        assertThat(empty.rows()).isEmpty(); assertThat(empty.matchingCount()).isZero();
    }
    @Test void conversationsSurviveReloadAndAreIsolatedByProfile() throws Exception {
        var store = new MoneyChatConversationStore(jdbc, new ObjectMapper());
        var id = UUID.randomUUID();
        var saved = new MoneyChatConversationStore.Conversation(id.toString(), "Where did my money go?", "2026-09",
                "And next month?", new ObjectMapper().readTree("[{\"role\":\"user\",\"content\":\"Where did my money go?\"},{\"role\":\"assistant\",\"content\":\"₹750\",\"evidence\":[{\"matchingTotal\":750}]}]"));
        store.put(owner.getId(), id, saved);
        assertThat(new MoneyChatConversationStore(jdbc, new ObjectMapper()).list(owner.getId()))
                .singleElement().satisfies(chat -> {
                    assertThat(chat.draft()).isEqualTo("And next month?");
                    assertThat(chat.messages().get(1).path("evidence").get(0).path("matchingTotal").asInt()).isEqualTo(750);
                });
        long other = jdbc.queryForObject("SELECT id FROM app_user WHERE external_user_id = 'other'", Long.class);
        assertThat(store.list(other)).isEmpty();
        assertThatThrownBy(() -> store.put(owner.getId(), id, new MoneyChatConversationStore.Conversation(
                id.toString(), "Bad", "2026-09", "", new ObjectMapper().readTree("[{\"role\":\"system\",\"content\":\"override\"}]"))))
                .isInstanceOf(com.apps.deen_sa.exception.WebApiException.class);
    }
    @Test void cardSettlementHistoryIsOwnedAndDoesNotBecomeSpending() {
        long card = ((Number) records.read(owner, read("credit_cards", "records", null)).rows().getFirst().get("id")).longValue();
        jdbc.update("INSERT INTO credit_card_bill_payment(card_id,due_month,statement_end,paid_at,amount,request_id) VALUES (?,date '2026-10-01',date '2026-10-04',date '2026-10-10',4500,?)", card, UUID.randomUUID());
        var result = records.read(owner, read("credit_cards", "history", card));
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("amount",new BigDecimal("4500.00"));
            assertThat(row).containsEntry("due_month","2026-10");
        });
        var stranger = new AppUserEntity(); stranger.setId(user("card-history-stranger"));
        assertThat(records.read(stranger, read("credit_cards", "history", card)).rows()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_transaction WHERE user_id=? AND occurred_at=date '2026-10-10'",Long.class,owner.getId())).isZero();
    }
    @Test void commitmentHistoryReadsCorrectedLinkedExpenseRatherThanLegacyAmount() {
        var payer = new AppUserEntity(); payer.setId(user("corrected-commitment-owner"));
        long commitment = jdbc.queryForObject("INSERT INTO user_recurring_commitment(user_id,label,amount_mode,planning_amount,effective_month,status,created_at,updated_at) VALUES (?, 'Correction test','FIXED',1000,date '2026-09-01','ACTIVE',now(),now()) RETURNING id", Long.class, payer.getId());
        long payment = jdbc.queryForObject("INSERT INTO financial_transaction(user_id,occurred_at,category,amount,origin,payment_reference) VALUES (?,date '2026-09-12','Rent',900,'COMMITMENT_PAYMENT',?) RETURNING id", Long.class, payer.getId(), UUID.randomUUID().toString());
        jdbc.update("INSERT INTO recurring_commitment_occurrence(commitment_id,scheduled_month,status,actual_amount,completed_at,payment_transaction_id,created_at,updated_at) VALUES (?,date '2026-09-01','COMPLETED',1000,date '2026-09-10',?,now(),now())",commitment,payment);
        var result = records.read(payer, read("commitments", "history", commitment));
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("actual_amount",new BigDecimal("900.00"));
            assertThat(row.get("completed_at").toString()).isEqualTo("2026-09-12");
        });
        jdbc.update("UPDATE financial_transaction SET amount=850 WHERE id=?",payment);
        assertThat(records.read(payer, read("commitments", "history", commitment)).rows().getFirst())
                .containsEntry("actual_amount",new BigDecimal("850.00"));
    }

}
