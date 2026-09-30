package com.apps.deen_sa.credits;

import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.insights.ExpenseChatModel;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/** Uses a unique disposable schema and exercises real PostgreSQL locks/constraints. */
@EnabledIfEnvironmentVariable(named="EXPENSE_CHAT_TEST_DB_URL",matches=".+")
class CreditStorePostgresTest {
    static JdbcTemplate jdbc;
    static DriverManagerDataSource dataSource;
    static String schema;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"),ZoneOffset.UTC);
    CreditStore store;
    long user;
    @BeforeAll static void setup() {
        schema="credit_test_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("EXPENSE_CHAT_TEST_DB_URL"), name=System.getenv("EXPENSE_CHAT_TEST_DB_USER"), password=System.getenv("EXPENSE_CHAT_TEST_DB_PASSWORD");
        var admin=new DriverManagerDataSource(url,name,password);
        Flyway.configure().dataSource(admin).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        dataSource=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,name,password);
        jdbc=new JdbcTemplate(dataSource);
    }
    @AfterAll static void cleanup() { if(jdbc!=null) jdbc.execute("DROP SCHEMA "+schema+" CASCADE"); }
    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE ai_credit_ledger,ai_credit_call,ai_credit_request,ai_credit_wallet,ai_credit_budget");
        store=store(policy("10","10",6),CLOCK);
        user=user();
    }
    long user() { return jdbc.queryForObject("INSERT INTO app_user(channel,external_user_id) VALUES ('WHATSAPP',?) RETURNING id",Long.class,UUID.randomUUID().toString()); }
    CreditPolicy policy(String daily,String request,int rpm) { return new CreditPolicy("test",new BigDecimal("100"),new BigDecimal("25"),new BigDecimal("400"),new BigDecimal(daily),new BigDecimal(request),rpm,true); }
    CreditStore store(CreditPolicy policy,Clock clock) { return new CreditStore(jdbc,new DataSourceTransactionManager(dataSource),policy,clock); }
    void grant(long who,String amount) { store.grant(who,UUID.randomUUID(),new BigDecimal(amount),user,"Test allowance"); }
    UUID start(long who) { UUID id=UUID.randomUUID(); store.start(who,id,"hash"); return id; }
    void code(Runnable action,String code) { assertThatThrownBy(action::run).isInstanceOfSatisfying(WebApiException.class,e->assertThat(e.code()).isEqualTo(code)); }
    @Test void newUsersHaveNoCreditsAndGrantsAreIdempotent() {
        code(()->start(user),"AI_CREDITS_EXHAUSTED");
        UUID op=UUID.randomUUID();
        store.grant(user,op,new BigDecimal("5.125"),user,"Welcome");
        store.grant(user,op,new BigDecimal("5.125"),user,"Welcome");
        assertThat(store.balance(user).available()).isEqualByComparingTo("5.125");
        assertThat(store.ledger(user)).hasSize(1);
        code(()->store.grant(user,op,new BigDecimal("8"),user,"Welcome"),"AI_REQUEST_CONFLICT");
        code(()->store.grant(user,UUID.randomUUID(),new BigDecimal("-1"),user,"Invalid"),"INVALID_CREDIT_AMOUNT");
    }
    @Test void cachedTokensAreNotChargedTwiceAndSettlementIsIdempotent() {
        grant(user,"5"); UUID request=start(user);
        UUID call=store.reserve(user,request,new BigDecimal("2"));
        assertThat(store.balance(user).available()).isEqualByComparingTo("3");
        var usage=new ExpenseChatModel.Usage(10000,4000,1000); // .6 + .1 + .4 = 1.1
        store.settle(call,usage); store.settle(call,usage);
        assertThat(store.balance(user).balance()).isEqualByComparingTo("3.9");
        assertThat(store.balance(user).reserved()).isZero();
        assertThat(store.ledger(user)).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT spent FROM ai_credit_budget",BigDecimal.class)).isEqualByComparingTo("1.1");
        store.finish(user,request,"{\"answer\":\"Done\"}",null);
        assertThat(store.start(user,request,"hash")).contains("Done");
        code(()->store.start(user,request,"different"),"AI_REQUEST_CONFLICT");
    }
    @Test void concurrentRequestsCannotStartTwiceForSameUser() throws Exception {
        grant(user,"5");
        var results=race(()->start(user),()->start(user));
        assertThat(results.stream().filter(UUID.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(WebApiException.class::isInstance)).hasSize(1);
    }
    @Test void twoInstancesCannotOverspendOneWallet() throws Exception {
        grant(user,"3"); UUID request=start(user);
        CreditStore other=store(policy("10","10",6),CLOCK);
        var results=race(()->store.reserve(user,request,new BigDecimal("2")),()->other.reserve(user,request,new BigDecimal("2")));
        assertThat(results.stream().filter(UUID.class::isInstance)).hasSize(1);
        assertThat(store.balance(user).available()).isEqualByComparingTo("1");
    }
    @Test void globalBudgetIsSharedAcrossUsersAndInstances() throws Exception {
        store=store(policy("3","10",6),CLOCK);
        long friend=user(); grant(user,"10"); grant(friend,"10");
        UUID first=start(user),second=start(friend);
        CreditStore other=store(policy("3","10",6),CLOCK);
        var results=race(()->store.reserve(user,first,new BigDecimal("2")),()->other.reserve(friend,second,new BigDecimal("2")));
        assertThat(results.stream().filter(UUID.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(WebApiException.class::isInstance).map(WebApiException.class::cast)).extracting(WebApiException::code).containsExactly("AI_DAILY_LIMIT");
    }
    @Test void unknownUsageStaysHeldAcrossRestartUntilVerifiedReview() {
        grant(user,"5"); UUID request=start(user); UUID call=store.reserve(user,request,new BigDecimal("2"));
        code(()->store.settle(call,null),"AI_USAGE_PENDING");
        store.finish(user,request,null,new WebApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"CHAT_UNAVAILABLE","Try later"));
        var restarted=store(policy("10","10",6),Clock.offset(CLOCK,Duration.ofMinutes(3)));
        assertThat(restarted.balance(user).reserved()).isEqualByComparingTo("2");
        code(()->restarted.start(user,UUID.randomUUID(),"new"),"AI_USAGE_PENDING");
        code(()->store.reconcile(call,BigDecimal.ZERO,user,"Verified no charge"),"AI_USAGE_PENDING");
        assertThat(restarted.pendingCalls()).hasSize(1);
        restarted.reconcile(call,new BigDecimal("0.5"),user,"Provider receipt verified");
        restarted.reconcile(call,new BigDecimal("0.5"),user,"Provider receipt verified");
        assertThat(restarted.balance(user).available()).isEqualByComparingTo("4.5");
        assertThat(restarted.pendingCalls()).isEmpty();
        assertThat(restarted.start(user,UUID.randomUUID(),"new")).isNull();
    }
    @Test void perQuestionCapIncludesAllCallsAndFailedQuestionsKeepActualCharges() {
        store=store(policy("10","1",6),CLOCK); grant(user,"5"); UUID request=start(user);
        UUID call=store.reserve(user,request,new BigDecimal("0.8"));
        store.settle(call,new ExpenseChatModel.Usage(5000,0,0));
        code(()->store.reserve(user,request,new BigDecimal("0.8")),"AI_QUESTION_LIMIT");
        var failure=new WebApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"AI_QUESTION_LIMIT","Shorten question");
        store.finish(user,request,null,failure);
        code(()->store.start(user,request,"hash"),"AI_QUESTION_LIMIT");
        assertThat(store.balance(user).available()).isEqualByComparingTo("4.5");
    }
    @Test void pauseRatesAndDailyResetAreEnforced() {
        grant(user,"5"); store.pause(user,true,user);
        code(()->start(user),"AI_ACCESS_PAUSED"); store.pause(user,false,user);
        store=store(policy("10","10",1),CLOCK); UUID request=start(user);
        store.finish(user,request,"{}",null); code(()->start(user),"AI_RATE_LIMIT");
        var tomorrow=store(policy("10","10",1),Clock.offset(CLOCK,Duration.ofDays(1)));
        UUID next=UUID.randomUUID(); tomorrow.start(user,next,"next"); tomorrow.reserve(user,next,BigDecimal.ONE);
        assertThat(jdbc.queryForObject("SELECT day FROM ai_credit_budget",LocalDate.class)).isEqualTo(LocalDate.parse("2026-10-01"));
    }
    @Test void staleRequestWithoutUncertainCallRecoversButReservedCallDoesNot() {
        grant(user,"5"); start(user);
        var restarted=store(policy("10","10",6),Clock.offset(CLOCK,Duration.ofMinutes(3)));
        UUID second=UUID.randomUUID(); restarted.start(user,second,"second");
        restarted.reserve(user,second,BigDecimal.ONE);
        var later=store(policy("10","10",6),Clock.offset(CLOCK,Duration.ofMinutes(6)));
        code(()->later.start(user,UUID.randomUUID(),"third"),"AI_USAGE_PENDING");
    }
    @Test void fullChatChargesScopeAndAnswerAndReplaysWithoutModelUse() throws Exception {
        grant(user,"10");
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var model=org.mockito.Mockito.mock(ExpenseChatModel.class);
        var tools=org.mockito.Mockito.mock(com.apps.deen_sa.insights.ExpenseMcpTools.class);
        org.mockito.Mockito.when(tools.definitions()).thenReturn(List.of());
        org.mockito.Mockito.when(model.complete(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new ExpenseChatModel.Reply("IN_SCOPE",List.of(),new ExpenseChatModel.Usage(100,0,10)),
                        new ExpenseChatModel.Reply("No recorded data.",List.of(),new ExpenseChatModel.Usage(500,100,30)));
        var service=new com.apps.deen_sa.insights.ExpenseChatService(model,tools,CLOCK,store,policy("10","10",6),mapper);
        var profile=new com.apps.deen_sa.entity.AppUserEntity(); profile.setId(user);
        var request=new com.apps.deen_sa.insights.ExpenseChatService.Request("Where did I spend?","2026-09",List.of());
        var result=service.chat(profile,request);
        assertThat(result.credits().available()).isEqualByComparingTo("9.9315");
        assertThat(store.ledger(user)).hasSize(3);
        assertThat(service.chat(profile,request).answer()).isEqualTo("No recorded data.");
        org.mockito.Mockito.verify(model,org.mockito.Mockito.times(2)).complete(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyList());
    }
    List<Object> race(Callable<Object> a,Callable<Object> b) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var tasks=List.of(a,b).stream().map(action->pool.submit(()->{gate.await(); try{return action.call();}catch(WebApiException e){return e;}})).toList();
            gate.countDown(); return List.of(tasks.get(0).get(10,TimeUnit.SECONDS),tasks.get(1).get(10,TimeUnit.SECONDS));
        }
    }
}
