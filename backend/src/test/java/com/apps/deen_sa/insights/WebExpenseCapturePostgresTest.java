package com.apps.deen_sa.insights;

import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.repository.*;
import com.apps.deen_sa.service.*;
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
import jakarta.persistence.*;
import java.util.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Migrates and drops only a uniquely named schema; never resets application tables. */
@EnabledIfEnvironmentVariable(named="EXPENSE_CHAT_TEST_DB_URL",matches=".+")
class WebExpenseCapturePostgresTest {
    static String schema;static JdbcTemplate jdbc;static EntityManagerFactory emf;static TransactionTemplate tx;
    static ManualExpenseCaptureService manual;static WebExpenseCaptureStore store;static ExpenseConfirmationCommandHandler handler;static AppUserEntity owner,other;
    @BeforeAll static void setup(){
        schema="capture_test_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("EXPENSE_CHAT_TEST_DB_URL"),name=System.getenv("EXPENSE_CHAT_TEST_DB_USER"),password=System.getenv("EXPENSE_CHAT_TEST_DB_PASSWORD");
        var admin=new DriverManagerDataSource(url,name,password);
        Flyway.configure().dataSource(admin).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        var ds=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,name,password);
        jdbc=new JdbcTemplate(ds);
        var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);
        factory.setPackagesToScan("com.apps.deen_sa.entity");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.default_schema",schema,"hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));factory.afterPropertiesSet();
        emf=factory.getObject();tx=new TransactionTemplate(new JpaTransactionManager(emf));
        var repositories=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        var drafts=repositories.getRepository(TransactionDraftRepository.class);
        var extractions=repositories.getRepository(TransactionDraftExtractionRepository.class);
        var refs=repositories.getRepository(UserReferenceEntityRepository.class);
        var aliases=repositories.getRepository(UserReferenceAliasRepository.class);
        var transactions=repositories.getRepository(FinancialTransactionRepository.class);
        store=new WebExpenseCaptureStore(drafts,extractions);
        manual=new ManualExpenseCaptureService(drafts,extractions,new ExpenseTaxonomyRegistry(),refs,aliases,java.time.Clock.fixed(java.time.Instant.parse("2026-10-01T00:00:00Z"),java.time.ZoneOffset.UTC));
        handler=new ExpenseConfirmationCommandHandler(extractions,new ConfirmedReferenceWriter(refs,aliases),
                new FinancialTransactionWriter(transactions,new ExpenseTaxonomyRegistry()),mock(MissingTransactionDateContextService.class));
        owner=user("owner");other=user("other");
    }
    static AppUserEntity user(String external){
        var user=new AppUserEntity();user.setId(jdbc.queryForObject("INSERT INTO app_user(channel,external_user_id) VALUES ('WEB_DEMO',?) RETURNING id",Long.class,external));return user;
    }
    static Long prepare(){
        var taxonomy=new ExpenseTaxonomyRegistry();var category=taxonomy.categories().iterator().next();var sub=taxonomy.subcategoriesFor(category).iterator().next();
        var request=new WebExpenseCaptureService.Request(UUID.randomUUID(),LocalDate.parse("2026-09-28"),"Paid 450",List.of());
        var preview=new WebExpenseCaptureService.Preview(new BigDecimal("450"),request.date(),category,sub,"Shop",null,"INR");
        return tx.execute(status->store.save(owner,request,preview,"Paid 450",true));
    }
    @Test void concurrentConfirmationsWriteOneOwnedTraceableExpense() throws Exception {
        Long id=prepare();
        // Simulate reference cleanup after preview, before confirmation.
        jdbc.update("INSERT INTO user_reference_entity(user_id,entity_type,canonical_name,active) VALUES (?, 'MERCHANT','Shop',false)",owner.getId());
        Long canonical=jdbc.queryForObject("INSERT INTO user_reference_entity(user_id,entity_type,canonical_name,active) VALUES (?, 'MERCHANT','Store',true) RETURNING id",Long.class,owner.getId());
        jdbc.update("INSERT INTO user_reference_alias(reference_entity_id,alias_text,source) VALUES (?,'Shop','MANUAL')",canonical);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_transaction WHERE user_id="+owner.getId(),Integer.class)).isZero();
        assertThatThrownBy(()->tx.execute(status->handler.handleWeb(other.getId(),id,true))).isInstanceOf(WebApiException.class);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var jobs=executor.invokeAll(List.of(()->tx.execute(status->handler.handleWeb(owner.getId(),id,true)),()->tx.execute(status->handler.handleWeb(owner.getId(),id,true))));
            for(var job:jobs)assertThat(job.get()).isEqualTo(LocalDate.parse("2026-09-28"));
        }finally{executor.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_transaction WHERE user_id="+owner.getId(),Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT d.source FROM financial_transaction t JOIN transaction_draft d ON d.id=t.source_draft_id WHERE t.user_id="+owner.getId(),String.class)).isEqualTo("WEB_APP");
        assertThat(jdbc.queryForObject("SELECT t.user_id FROM financial_transaction t WHERE t.user_id="+owner.getId(),Long.class)).isEqualTo(owner.getId());
        assertThat(jdbc.queryForObject("SELECT status FROM transaction_draft_extraction WHERE id=?",String.class,id)).isEqualTo("USED");
        assertThat(jdbc.queryForObject("SELECT merchant_id FROM financial_transaction WHERE user_id="+owner.getId(),Long.class)).isEqualTo(canonical);
        Long cancelled=prepare();tx.execute(status->handler.handleWeb(owner.getId(),cancelled,false));
        assertThatThrownBy(()->tx.execute(status->handler.handleWeb(owner.getId(),cancelled,true))).isInstanceOf(WebApiException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_transaction WHERE user_id="+owner.getId(),Integer.class)).isEqualTo(1);
    }
    @Test void manualPreparationRetriesConflictIsolationCancellationAndConfirmation() throws Exception {
        var taxonomy=new ExpenseTaxonomyRegistry();var category=taxonomy.categories().iterator().next();var sub=taxonomy.subcategoriesFor(category).iterator().next();
        var request=new ManualExpenseCaptureService.Request(UUID.randomUUID(),LocalDate.parse("2026-09-27"),new BigDecimal("12.345"),category,sub,"New manual shop",null);
        var executor=Executors.newFixedThreadPool(2);
        Long id;
        try {
            var jobs=executor.invokeAll(List.<Callable<WebExpenseCaptureService.Response>>of(()->tx.execute(status->manual.prepare(other,request)),()->tx.execute(status->manual.prepare(other,request))));
            var first=jobs.get(0).get();assertThat(jobs.get(1).get()).isEqualTo(first);id=first.extractionId();
            assertThat(first.preview().amount()).isEqualByComparingTo("12.35");
        } finally {executor.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_transaction WHERE user_id=?",Integer.class,other.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_reference_entity WHERE user_id=?",Integer.class,other.getId())).isZero();
        var changed=new ManualExpenseCaptureService.Request(request.requestId(),request.date(),new BigDecimal("20"),category,sub,request.merchant(),null);
        assertThatThrownBy(()->tx.execute(status->manual.prepare(other,changed))).isInstanceOf(WebApiException.class);
        assertThatThrownBy(()->tx.execute(status->handler.handleWeb(owner.getId(),id,true))).isInstanceOf(WebApiException.class);
        tx.execute(status->handler.handleWeb(other.getId(),id,true));tx.execute(status->handler.handleWeb(other.getId(),id,true));
        assertThat(tx.execute(status->manual.prepare(other,request)).extractionId()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_transaction WHERE user_id=?",Integer.class,other.getId())).isEqualTo(1);
        var cancelRequest=new ManualExpenseCaptureService.Request(UUID.randomUUID(),request.date(),request.amount(),category,sub,null,null);
        var cancelled=tx.execute(status->manual.prepare(other,cancelRequest));
        tx.execute(status->handler.handleWeb(other.getId(),cancelled.extractionId(),false));
        assertThatThrownBy(()->tx.execute(status->manual.prepare(other,cancelRequest))).isInstanceOf(WebApiException.class);
        assertThatThrownBy(()->tx.execute(status->handler.handleWeb(other.getId(),cancelled.extractionId(),true))).isInstanceOf(WebApiException.class);
    }
    @AfterAll static void cleanup(){if(emf!=null)emf.close();if(jdbc!=null)jdbc.execute("DROP SCHEMA "+schema+" CASCADE");}
}
