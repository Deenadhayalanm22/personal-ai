package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.repository.*;
import com.apps.deen_sa.exception.WebApiException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses a unique migrated schema and leaves existing application records untouched. */
@EnabledIfEnvironmentVariable(named="EXPENSE_CHAT_TEST_DB_URL", matches=".+")
class CreditCardBillPostgresTest {
    static String schema;
    static JdbcTemplate jdbc;
    static EntityManagerFactory emf;
    static TransactionTemplate tx;
    static CreditCardBillService service;
    static FinancialTransactionRepository transactions;
    static AppUserEntity owner, other;
    static long account, cardId;
    static final YearMonth NOVEMBER = YearMonth.of(2026,11);

    @BeforeAll static void setup() {
        schema="card_bill_test_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("EXPENSE_CHAT_TEST_DB_URL"), name=System.getenv("EXPENSE_CHAT_TEST_DB_USER"), password=System.getenv("EXPENSE_CHAT_TEST_DB_PASSWORD");
        var admin=new DriverManagerDataSource(url,name,password);
        Flyway.configure().dataSource(admin).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        var ds=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,name,password);
        jdbc=new JdbcTemplate(ds);
        var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);
        factory.setPackagesToScan("com.apps.deen_sa.entity");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.default_schema",schema,"hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));factory.afterPropertiesSet();
        emf=factory.getObject();tx=new TransactionTemplate(new JpaTransactionManager(emf));
        var repositories=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        transactions=repositories.getRepository(FinancialTransactionRepository.class);
        service=new CreditCardBillService(repositories.getRepository(UserCreditCardRepository.class),transactions,jdbc,
                Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"),ZoneOffset.UTC));
        owner=user("owner");other=user("other");
        account=jdbc.queryForObject("INSERT INTO user_reference_entity(user_id,entity_type,canonical_name) VALUES (?, 'ACCOUNT','HDFC credit card') RETURNING id",Long.class,owner.getId());
        cardId=jdbc.queryForObject("INSERT INTO user_credit_card(user_id,account_reference_id,card_name,issuer_name,statement_day,due_day) VALUES (?,?,'Millennia','HDFC',28,5) RETURNING id",Long.class,owner.getId(),account);
    }
    static AppUserEntity user(String name) {
        var user=new AppUserEntity();user.setId(jdbc.queryForObject("INSERT INTO app_user(channel,external_user_id) VALUES ('WEB_DEMO',?) RETURNING id",Long.class,name));
        user.setCurrency("INR");user.setTimezone("Asia/Kolkata");return user;
    }
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM credit_card_bill_payment");jdbc.update("DELETE FROM financial_transaction");jdbc.update("DELETE FROM transaction_draft");
        jdbc.update("UPDATE user_credit_card SET start_month=NULL WHERE id=?",cardId);
        purchase("2026-10-01", "2000");purchase("2026-10-27", "3000");
        purchase("2026-10-28", "700"); // next statement, excluded from November bill
    }
    static void purchase(String date, String amount) {
        long draft=jdbc.queryForObject("INSERT INTO transaction_draft(user_id,input_type,source,source_message_id,status) VALUES (?, 'TEXT','WEB_APP',?,'CONSUMED') RETURNING id",Long.class,owner.getId(),UUID.randomUUID().toString());
        jdbc.update("INSERT INTO financial_transaction(user_id,source_draft_id,occurred_at,category,subcategory,amount,source_account_id) VALUES (?,?,cast(? as date),'Food','Groceries',?,?)",owner.getId(),draft,date,new BigDecimal(amount),account);
    }
    CreditCardBillService.PaymentRequest request(String month, String amount, String date) {
        return new CreditCardBillService.PaymentRequest(UUID.randomUUID(),month,new BigDecimal(amount),LocalDate.parse(date));
    }
    CreditCardBillService.Bill record(CreditCardBillService.PaymentRequest r) {
        return tx.execute(status->service.record(owner,cardId,r));
    }
    @Test void boundedChatSummariesUseCanonicalAmountsAndOwnedStartMonth() {
        jdbc.update("UPDATE user_credit_card SET start_month=date '2026-11-01' WHERE id=?",cardId);
        assertThat(tx.execute(status->service.summaries(owner,YearMonth.of(2026,10),1,0)).matchingCount()).isZero();
        record(request("2026-11","5500","2026-11-01"));
        var page=tx.execute(status->service.summaries(owner,NOVEMBER,1,0));
        assertThat(page.matchingCount()).isEqualTo(1);
        var bill=page.bills().getFirst();
        assertThat(bill.projectedAmount()).isEqualByComparingTo("5000");
        assertThat(bill.paidAmount()).isEqualByComparingTo("5500");
        assertThat(bill.remaining()).isZero();
        assertThat(bill.unmatchedPaymentAmount()).isEqualByComparingTo("500");
        assertThat(bill.payments()).isEmpty();
        assertThat(tx.execute(status->service.summaries(owner,NOVEMBER,1,1)).bills()).isEmpty();
        assertThat(tx.execute(status->service.summaries(other,NOVEMBER,1,0)).matchingCount()).isZero();
    }

    @Test void startMonthScopesBillsButNotPurchaseMonthSpending() {
        purchase("2026-11-01","900");
        jdbc.update("UPDATE user_credit_card SET start_month=date '2026-11-01' WHERE id=?",cardId);
        var october=tx.execute(status->service.list(owner,YearMonth.of(2026,10)));
        assertThat(october.bills()).isEmpty();
        var november=tx.execute(status->service.list(owner,NOVEMBER)).bills().getFirst();
        assertThat(november.projectedAmount()).isEqualByComparingTo("5000");
        assertThat(november.monthlyPurchaseAmount()).isEqualByComparingTo("900");
        assertThatThrownBy(()->record(request("2026-10","1","2026-10-05"))).isInstanceOf(WebApiException.class).hasMessageContaining("start month");
        record(request("2026-11","2000","2026-11-01"));
        assertThat(tx.execute(status->service.list(owner,NOVEMBER)).bills().getFirst().monthlyPurchaseAmount()).isEqualByComparingTo("900");
        var repos=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        var profiles=new WebCreditCardService(repos.getRepository(UserCreditCardRepository.class),repos.getRepository(UserReferenceEntityRepository.class),mock(MonthlyFinancialSnapshotService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(profiles,"jdbc",jdbc);
        assertThatThrownBy(()->tx.execute(status->profiles.update(owner,cardId,new WebCreditCardService.CardRequest(null,null,null,null,null,null,"2026-12")))).isInstanceOf(WebApiException.class).hasMessageContaining("cannot change");
    }

    @Test void partialThenFullSettlementChangesDueAmountButNeverSpending() {
        var first=request("2026-11","2000","2026-11-01");
        var partial=record(first);
        assertThat(partial.projectedAmount()).isEqualByComparingTo("5000");
        assertThat(partial.remaining()).isEqualByComparingTo("3000");
        assertThat(partial.statementEnd()).isEqualTo(LocalDate.parse("2026-10-27"));
        assertThat(record(first).payments()).hasSize(1);
        var full=record(request("2026-11","3000","2026-11-05"));
        assertThat(full.remaining()).isEqualByComparingTo("0");assertThat(full.payments()).hasSize(2);
        tx.executeWithoutResult(status->{
            var calendar=new FinancialTransactionCalendarService(transactions);
            assertThat(calendar.calendar(owner,YearMonth.of(2026,10)).totalSpend()).isEqualByComparingTo("5700");
            assertThat(calendar.calendar(owner,YearMonth.of(2026,10)).creditCardSpend()).isEqualByComparingTo("5700");
            assertThat(calendar.calendar(owner,NOVEMBER).totalSpend()).isEqualByComparingTo("0");
            assertThat(calendar.calendar(owner,NOVEMBER).transactionCount()).isZero();
        });
        assertThat(new FinancialActivityService(jdbc).list(owner,YearMonth.of(2026,10)).items())
                .allSatisfy(item -> assertThat(item.description()).isEqualTo("On credit card · bill paid separately"));
        var feed=new FinancialActivityService(jdbc).list(owner,NOVEMBER);
        assertThat(feed.items()).extracting(FinancialActivityService.ActivityItem::type).containsExactly("CARD_PAYMENT","CARD_PAYMENT");
        assertThat(feed.items()).extracting(FinancialActivityService.ActivityItem::date).containsExactly(LocalDate.parse("2026-11-05"),LocalDate.parse("2026-11-01"));
        assertThat(new FinancialActivityService(jdbc).list(other,NOVEMBER).items()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_transaction",Long.class)).isEqualTo(3L);
        jdbc.update("UPDATE financial_transaction SET amount=100 WHERE occurred_at=date '2026-10-27'");
        var corrected=tx.execute(status->service.list(owner,NOVEMBER)).bills().getFirst();
        assertThat(corrected.paidAmount()).isEqualByComparingTo("5000");assertThat(corrected.remaining()).isZero();
    }
    @Test void actualPaymentRetainsDifferenceAndLaterPurchasesReconcileIt() {
        jdbc.update("UPDATE financial_transaction SET amount=72000 WHERE occurred_at=date '2026-10-27'");
        purchase("2026-10-15","1000");
        var request=request("2026-11","88000","2026-11-01");
        var paid=record(request);
        assertThat(paid.projectedAmount()).isEqualByComparingTo("75000");
        assertThat(paid.paidAmount()).isEqualByComparingTo("88000");
        assertThat(paid.unmatchedPaymentAmount()).isEqualByComparingTo("13000");
        assertThat(paid.remaining()).isZero();
        assertThat(record(request).payments()).hasSize(1);
        purchase("2026-10-16","10000");
        var corrected=tx.execute(status->service.list(owner,NOVEMBER)).bills().getFirst();
        assertThat(corrected.unmatchedPaymentAmount()).isEqualByComparingTo("3000");
        assertThat(corrected.paidAmount()).isEqualByComparingTo("88000");
        assertThat(new FinancialActivityService(jdbc).list(owner,NOVEMBER).items()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_transaction WHERE occurred_at >= date '2026-11-01'",Long.class)).isZero();
        jdbc.update("DELETE FROM financial_transaction");
        var additional=record(request("2026-11","500","2026-11-02"));
        assertThat(additional.unmatchedPaymentAmount()).isEqualByComparingTo("88500");
        assertThat(additional.payments()).hasSize(2);
        jdbc.update("DELETE FROM credit_card_bill_payment");
        var uncaptured=record(request("2026-11","85000","2026-11-02"));
        assertThat(uncaptured.projectedAmount()).isZero();
        assertThat(uncaptured.unmatchedPaymentAmount()).isEqualByComparingTo("85000");
    }
    @Test void rejectsForeignInvalidAndChangedRetryWithoutSideEffects() {
        assertThatThrownBy(()->tx.execute(status->service.record(other,cardId,request("2026-11","100","2026-11-01"))))
                .isInstanceOf(WebApiException.class).hasMessageContaining("not found");
        for (var r : List.of(request("2026-11","0","2026-11-01"),request("2026-11","-1","2026-11-01"),
                request("2026-11","1.001","2026-11-01"),
                request("2026-11","1","2026-11-06"),request("2026-11","1","2026-10-27"),
                request("2026-12","1","2026-11-05")))
            assertThatThrownBy(()->record(r)).isInstanceOf(WebApiException.class);
        var r=request("2026-11","100","2026-11-01");record(r);
        assertThatThrownBy(()->record(new CreditCardBillService.PaymentRequest(r.requestId(),r.month(),new BigDecimal("101"),r.paidAt())))
                .isInstanceOf(WebApiException.class).hasMessageContaining("different details");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_card_bill_payment",Long.class)).isEqualTo(1L);
    }
    @Test void concurrentRetriesAndCompetingPaymentsSerialize() throws Exception {
        var executor=Executors.newFixedThreadPool(2);
        try {
            var r=request("2026-11","3000","2026-11-01");
            var futures=executor.invokeAll(List.<Callable<CreditCardBillService.Bill>>of(()->record(r),()->record(r)));
            for(var f:futures)assertThat(f.get().remaining()).isEqualByComparingTo("2000");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_card_bill_payment",Long.class)).isEqualTo(1L);
            var competitors=executor.invokeAll(List.<Callable<Boolean>>of(
                ()->{try{record(request("2026-11","2000","2026-11-02"));return true;}catch(WebApiException e){return false;}},
                ()->{try{record(request("2026-11","2000","2026-11-02"));return true;}catch(WebApiException e){return false;}}));
            assertThat(competitors.stream().map(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).toList()).containsExactly(true,true);
            assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM credit_card_bill_payment",BigDecimal.class)).isEqualByComparingTo("7000");
            assertThat(tx.execute(status->service.list(owner,NOVEMBER)).bills().getFirst().unmatchedPaymentAmount()).isEqualByComparingTo("2000");
        } finally {executor.shutdownNow();}
    }
    @Test void generationDayPurchaseIsProjectedForNextMonth() {
        jdbc.update("DELETE FROM financial_transaction"); jdbc.update("DELETE FROM transaction_draft");
        jdbc.update("UPDATE user_credit_card SET statement_day=1, due_day=21 WHERE id=?",cardId);
        purchase("2026-10-01","480");
        try {
            tx.executeWithoutResult(status -> {
                var october=service.list(owner,YearMonth.of(2026,10)).bills().getFirst();
                var november=service.list(owner,NOVEMBER).bills().getFirst();
                assertThat(october.projectedAmount()).isZero();
                assertThat(november.projectedAmount()).isEqualByComparingTo("480");
                assertThat(november.periodStart()).isEqualTo(LocalDate.of(2026,10,1));
                assertThat(november.statementEnd()).isEqualTo(LocalDate.of(2026,10,31));
                assertThat(november.statementGeneratedAt()).isEqualTo(LocalDate.of(2026,11,1));
                assertThat(november.dueDate()).isEqualTo(LocalDate.of(2026,11,21));
            });
        } finally { jdbc.update("UPDATE user_credit_card SET statement_day=28, due_day=5 WHERE id=?",cardId); }
    }
    @Test void boundaryMigrationPreservesExistingPaymentAmountsDatesAndDueMonth() {
        String migrationSchema="card_boundary_"+UUID.randomUUID().toString().replace("-","");
        var ds=new DriverManagerDataSource(System.getenv("EXPENSE_CHAT_TEST_DB_URL"),System.getenv("EXPENSE_CHAT_TEST_DB_USER"),System.getenv("EXPENSE_CHAT_TEST_DB_PASSWORD"));
        var migrationJdbc=new JdbcTemplate(ds);
        try {
            Flyway.configure().dataSource(ds).schemas(migrationSchema).defaultSchema(migrationSchema).locations("classpath:db/migration").target("38").load().migrate();
            // JdbcTemplate opens a new connection per call, so qualify the fixture tables.
            long user=migrationJdbc.queryForObject("INSERT INTO "+migrationSchema+".app_user(channel,external_user_id) VALUES ('WEB_DEMO','migration') RETURNING id",Long.class);
            long ref=migrationJdbc.queryForObject("INSERT INTO "+migrationSchema+".user_reference_entity(user_id,entity_type,canonical_name) VALUES (?, 'ACCOUNT','Card') RETURNING id",Long.class,user);
            long card=migrationJdbc.queryForObject("INSERT INTO "+migrationSchema+".user_credit_card(user_id,account_reference_id,card_name,issuer_name,statement_day,due_day) VALUES (?,?,'Card','Bank',1,21) RETURNING id",Long.class,user,ref);
            UUID requestId=UUID.randomUUID();
            migrationJdbc.update("INSERT INTO "+migrationSchema+".credit_card_bill_payment(card_id,due_month,statement_end,paid_at,amount,request_id) VALUES (?,date '2026-10-01',date '2026-10-01',date '2026-10-02',480,?)",card,requestId);
            Flyway.configure().dataSource(ds).schemas(migrationSchema).defaultSchema(migrationSchema).locations("classpath:db/migration").load().migrate();
            migrationJdbc.query("SELECT statement_end,due_month,paid_at,amount,request_id FROM "+migrationSchema+".credit_card_bill_payment",rs -> {
                assertThat(rs.getDate("statement_end").toLocalDate()).isEqualTo(LocalDate.of(2026,9,30));
                assertThat(rs.getDate("due_month").toLocalDate()).isEqualTo(LocalDate.of(2026,10,1));
                assertThat(rs.getDate("paid_at").toLocalDate()).isEqualTo(LocalDate.of(2026,10,2));
                assertThat(rs.getBigDecimal("amount")).isEqualByComparingTo("480");
                assertThat(rs.getObject("request_id")).isEqualTo(requestId);
            });
        } finally {migrationJdbc.execute("DROP SCHEMA IF EXISTS "+migrationSchema+" CASCADE");}
    }
    @Test void paidStatementsKeepTheirBillingIdentity() {
        record(request("2026-11","100","2026-11-01"));
        var repositories=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        var profiles=new WebCreditCardService(repositories.getRepository(UserCreditCardRepository.class),repositories.getRepository(UserReferenceEntityRepository.class),mock(MonthlyFinancialSnapshotService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(profiles,"jdbc",jdbc);
        assertThatThrownBy(()->tx.execute(status->profiles.update(owner,cardId,new WebCreditCardService.CardRequest(null,null,null,27,null,null))))
            .isInstanceOf(WebApiException.class).hasMessageContaining("cannot change");
        tx.executeWithoutResult(status->profiles.update(owner,cardId,new WebCreditCardService.CardRequest(null,"Renamed",null,null,null,null)));
    }
    @AfterAll static void cleanup() {
        if(emf!=null)emf.close();if(jdbc!=null)jdbc.execute("DROP SCHEMA "+schema+" CASCADE");
    }
}
