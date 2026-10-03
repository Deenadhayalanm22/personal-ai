package com.apps.deen_sa.insights;

import com.apps.deen_sa.credits.*;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.*;
import com.apps.deen_sa.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class WebExpenseCaptureServiceTest {
    final ExpenseChatModel model=mock(ExpenseChatModel.class);
    final CreditStore credits=mock(CreditStore.class);
    final ObjectMapper mapper=new ObjectMapper().registerModule(new JavaTimeModule());
    final UserReferenceEntityRepository refs=mock(UserReferenceEntityRepository.class);
    final UserReferenceAliasRepository aliases=mock(UserReferenceAliasRepository.class);
    final WebExpenseCaptureStore store=mock(WebExpenseCaptureStore.class);
    final AppUserEntity user=new AppUserEntity();
    final ExpenseTaxonomyRegistry taxonomy=new ExpenseTaxonomyRegistry();
    final CreditPolicy policy=new CreditPolicy("test",BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ONE,6,true);
    final ExpenseMcpTools tools=mock(ExpenseMcpTools.class);
    final WebExpenseCaptureService service=createService();
    WebExpenseCaptureService createService(){try{return new WebExpenseCaptureService(model,credits,policy,mapper,taxonomy,refs,aliases,tools,store,
            Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"),ZoneOffset.UTC));}catch(Exception ex){throw new RuntimeException(ex);}}
    WebExpenseCaptureServiceTest(){user.setId(42L);user.setChannel("WEB_DEMO");user.setExternalUserId("demo");}
    WebExpenseCaptureService.Request request(String message,List<String> turns){return new WebExpenseCaptureService.Request(UUID.randomUUID(),LocalDate.parse("2026-09-28"),message,turns);}
    void reply(String json){when(model.complete(anyString(),anyList(),anyList())).thenReturn(new ExpenseChatModel.Reply(null,List.of(new ExpenseChatModel.Call("capture","prepare_expense",json)),new ExpenseChatModel.Usage(100,0,50)));}
    String facts(String amount,String date){String category=taxonomy.categories().iterator().next(),sub=taxonomy.subcategoriesFor(category).iterator().next();return "{\"amount\":"+amount+",\"date\":"+date+",\"category\":\""+category+"\",\"subcategory\":\""+sub+"\",\"merchant\":\"SB\",\"account\":null,\"eligible\":true}";}
    @Test void preparesOwnedAliasPreviewForSelectedDateAndDoesNotWriteFinancialTransaction(){
        var ref=new UserReferenceEntity();ref.setId(8L);ref.setCanonicalName("Saravana Bhavan");ref.setActive(true);
        var alias=new UserReferenceAliasEntity();alias.setAliasText("SB");
        when(refs.findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.MERCHANT)).thenReturn(List.of(ref));
        when(aliases.findByReferenceEntityId(8L)).thenReturn(List.of(alias));
        when(store.save(eq(user),any(),any(),anyString(),eq(true))).thenReturn(123L);
        reply(facts("450.126","null"));
        var response=service.capture(user,request("Paid 450.126 at SB for groceries",List.of()));
        assertThat(response.status()).isEqualTo("READY");assertThat(response.preview().date()).isEqualTo(LocalDate.parse("2026-09-28"));
        assertThat(response.preview().amount()).isEqualByComparingTo("450.13");assertThat(response.preview().merchant()).isEqualTo("Saravana Bhavan");
        assertThat(response.extractionId()).isEqualTo(123L);
        verify(model).complete(contains("Today: 2026-10-01"),anyList(),argThat(definitions -> definitions.stream().anyMatch(tool -> tool.path("name").asText().equals("prepare_expense"))));
        verify(refs,atLeastOnce()).findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.MERCHANT);
        verify(credits).settle(nullable(UUID.class),eq(new ExpenseChatModel.Usage(100,0,50)));
    }
    @Test void missingAmountReturnsStatementAndRetainsPriorUserStatements(){
        reply(facts("null","null"));
        var response=service.capture(user,request("For groceries",List.of("Bought groceries at SB")));
        assertThat(response.status()).isEqualTo("NEEDS_DETAILS");assertThat(response.answer()).isEqualTo(WebExpenseCaptureService.INCOMPLETE);
        verify(store).save(eq(user),any(),any(),contains("Bought groceries at SB"),eq(false));
        verify(model).complete(anyString(),argThat(messages->messages.size()==2 && messages.get(0).content().equals("Bought groceries at SB")),anyList());
    }
    @Test void invalidTaxonomyAndRoundedZeroNeverProduceConfirmablePreview(){
        reply(facts("0.001","null"));
        assertThat(service.capture(user,request("Paid a tiny amount",List.of())).status()).isEqualTo("NEEDS_DETAILS");
        reply(facts("450","null").replace(taxonomy.categories().iterator().next(),"Unknown"));
        assertThat(service.capture(user,request("Paid 450",List.of())).status()).isEqualTo("NEEDS_DETAILS");
        verify(store,never()).save(any(),any(),any(),anyString(),eq(true));
    }
    @Test void explicitDateIsPreviewedAndFutureDateCannotBeConfirmed(){
        reply(facts("450","\"2026-09-27\""));
        assertThat(service.capture(user,request("Paid 450 on Sep 27",List.of())).preview().date()).isEqualTo(LocalDate.parse("2026-09-27"));
        reply(facts("450","\"2026-10-02\""));
        assertThat(service.capture(user,request("Paid 450 tomorrow",List.of())).status()).isEqualTo("NEEDS_DETAILS");
    }
    @Test void rejectsBadRequestBeforeSpendingCredits(){
        assertThatThrownBy(()->service.capture(user,new WebExpenseCaptureService.Request(UUID.randomUUID(),LocalDate.parse("2026-10-02"),"Paid 450",List.of()))).isInstanceOf(WebApiException.class);
        assertThatThrownBy(()->service.capture(user,request("x".repeat(2001),List.of()))).isInstanceOf(WebApiException.class);
        verifyNoInteractions(model,credits,store);
    }
    @Test void completedRetryReturnsCachedPreviewWithoutCallingModelOrSavingAgain() throws Exception {
        var request=request("Paid 450",List.of());
        var result=new WebExpenseCaptureService.Response("READY","Review",123L,null);
        when(credits.start(eq(42L),eq(request.requestId()),anyString())).thenReturn(mapper.writeValueAsString(result));
        assertThat(service.capture(user,request)).isEqualTo(result);verifyNoInteractions(model,store,refs);
    }
    @Test void malformedProviderResultRetainsCreditAccountingAndWritesNothing(){
        reply("not JSON");assertThatThrownBy(()->service.capture(user,request("Paid 450",List.of()))).isInstanceOf(WebApiException.class);
        verify(credits).finish(eq(42L),any(),isNull(),any(WebApiException.class));verifyNoInteractions(store);
    }
    ExpenseChatModel.Reply toolReply(String json) {
        return new ExpenseChatModel.Reply(null,List.of(new ExpenseChatModel.Call("capture","prepare_expense",json)),new ExpenseChatModel.Usage(100,0,50));
    }
    UserReferenceEntity reference(long id,String name) {
        var ref=new UserReferenceEntity();ref.setId(id);ref.setCanonicalName(name);return ref;
    }
    @Test void whatsappAndPortalReuseIdenticalPreparationWithChannelSpecificAccounting() {
        var text="KK kadai la 25 selavu panninen";
        var date=LocalDate.parse("2026-10-01");
        reply(facts("25","null"));
        var request=new WebExpenseCaptureService.Request(UUID.randomUUID(),date,text,List.of());
        var web=service.capture(user,request);
        clearInvocations(model,credits,store);
        var whatsapp=service.prepare(user,date,List.of(text));
        assertThat(whatsapp.ready()).isTrue();
        assertThat(whatsapp.facts().amount()).isEqualByComparingTo(web.preview().amount());
        assertThat(whatsapp.facts().date()).isEqualTo(web.preview().date());
        assertThat(whatsapp.facts().category()).isEqualTo(web.preview().category());
        verify(model).complete(contains("Never ask follow-up questions"),anyList(),anyList());
        verifyNoInteractions(credits,store);
    }
    @Test void looksUpFreshOwnedMcpEvidenceBeforeTerminalPreparationAndMetersBothTurns() {
        var lookup=new ExpenseChatModel.Call("lookup","query_expenses","{}");
        var result=Map.<String,Object>of("isError",false,"structuredContent",Map.of("merchant","SB"));
        when(tools.callResult(user,"query_expenses","{}")).thenReturn(result);
        when(tools.json(result)).thenReturn("{\"merchant\":\"SB\"}");
        when(model.complete(anyString(),anyList(),anyList())).thenReturn(
                new ExpenseChatModel.Reply(null,List.of(lookup),new ExpenseChatModel.Usage(100,0,50)),toolReply(facts("450","null")));
        assertThat(service.capture(user,request("Paid 450 at SB",List.of())).status()).isEqualTo("READY");
        verify(tools).callResult(user,"query_expenses","{}");
        verify(model).complete(anyString(),argThat(messages->messages.stream().anyMatch(m->m.role().equals("tool") && "lookup".equals(m.toolCallId()) && m.content().contains("SB"))),anyList());
        verify(credits,times(2)).reserve(eq(42L),any(),any());
        verify(credits,times(2)).settle(nullable(UUID.class),any());
    }
    @Test void ambiguousOptionalAliasStaysBlankWithoutBlockingTheExpense() {
        var alias=new UserReferenceAliasEntity();alias.setAliasText("SB");
        when(refs.findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.MERCHANT))
                .thenReturn(List.of(reference(1,"Shop One"),reference(2,"Shop Two")));
        when(aliases.findByReferenceEntityId(anyLong())).thenReturn(List.of(alias));
        reply(facts("450","null"));
        var response=service.capture(user,request("Paid 450 for groceries at SB",List.of()));
        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.preview().merchant()).isNull();
    }
    @Test void canonicalNameWinsEvenWhenAnotherOwnedReferenceUsesItAsAnAlias() {
        var alias=new UserReferenceAliasEntity();alias.setAliasText("SB");
        when(refs.findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.MERCHANT))
                .thenReturn(List.of(reference(1,"SB"),reference(2,"Shop Two")));
        when(aliases.findByReferenceEntityId(2L)).thenReturn(List.of(alias));
        reply(facts("450","null"));
        assertThat(service.capture(user,request("Paid 450 at SB",List.of())).preview().merchant()).isEqualTo("SB");
    }
    @Test void savedUpiAliasResolvesOnBothChannelsWithoutModelAccount() {
        var alias=new UserReferenceAliasEntity(); alias.setAliasText("UPI");
        when(refs.findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.ACCOUNT))
                .thenReturn(List.of(reference(1,"HDFC bank account")));
        when(aliases.findByReferenceEntityId(1L)).thenReturn(List.of(alias));
        reply(facts("170","null"));
        String message="Yesterday spent around 170 on chicken for dinner cooking paid from upi";
        assertThat(service.capture(user,request(message,List.of())).preview().account()).isEqualTo("HDFC bank account");
        assertThat(service.prepare(user,LocalDate.parse("2026-10-01"),List.of(message)).facts().account()).isEqualTo("HDFC bank account");
        assertThat(service.capture(user,request("Paid 170 for chicken from upington",List.of())).preview().account()).isNull();
        assertThat(service.capture(user,request("Paid 170 for chicken",List.of())).preview().account()).isNull();
        when(refs.findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.ACCOUNT))
                .thenReturn(List.of(reference(1,"HDFC bank account"),reference(2,"ICICI bank account")));
        when(aliases.findByReferenceEntityId(2L)).thenReturn(List.of(alias));
        assertThat(service.capture(user,request(message,List.of())).preview().account()).isNull();
    }
    @Test void genericCardDoesNotGuessBetweenAccountsEvenIfModelChoosesOne() {
        when(refs.findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.ACCOUNT))
                .thenReturn(List.of(reference(1,"HDFC credit card"),reference(2,"ICICI credit card")));
        reply(facts("450","null").replace("\"account\":null","\"account\":\"HDFC credit card\""));
        assertThat(service.capture(user,request("Paid 450 for groceries using credit card",List.of())).preview().account()).isNull();
    }
    @Test void genericAccountResolvesOnlyOneMatchingTypeAndExplicitCardIsNotSwallowed() {
        when(refs.findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.ACCOUNT))
                .thenReturn(List.of(reference(1,"HDFC bank account"),reference(2,"ICICI credit card")));
        reply(facts("450","null"));
        assertThat(service.capture(user,request("Paid 450 from bank accoujnt",List.of())).preview().account()).isEqualTo("HDFC bank account");
        assertThat(service.capture(user,request("Paid 450 using credit card",List.of())).preview().account()).isEqualTo("ICICI credit card");
        reply(facts("450","null").replace("\"account\":null","\"account\":\"HDFC credit card\""));
        assertThat(service.capture(user,request("Paid 450 using HDFC credit card",List.of())).preview().account()).isEqualTo("HDFC credit card");
    }
    @Test void ineligibleExpenseCannotProducePreviewEvenWithCompleteFacts() {
        reply(facts("450","null").replace("\"eligible\":true","\"eligible\":false"));
        var response=service.capture(user,request("Plan to buy groceries for 450",List.of()));
        assertThat(response.status()).isEqualTo("NEEDS_DETAILS");
        assertThat(response.answer()).doesNotContain("?").isEqualTo(WebExpenseCaptureService.PORTAL_UPDATE);
        verify(store).save(eq(user),any(),any(),anyString(),eq(false));
    }
    @Test void strictCaptureArgumentsRejectOwnerFieldsCoercionMissingFieldsAndTrailingJson() {
        for(String json:List.of(facts("450","null").replace("\"eligible\":true","\"eligible\":true,\"userId\":1"),
                facts("\"450\"","null"),facts("450","null").replace(",\"eligible\":true",""),facts("450","null")+" {}")) {
            reply(json);
            assertThatThrownBy(()->service.capture(user,request("Paid 450",List.of()))).isInstanceOf(WebApiException.class);
        }
        verifyNoInteractions(store);
    }
    @Test void freeTextQuestionsCannotBecomePreviewsAndLoopStopsAtThreeTurns() {
        when(model.complete(anyString(),anyList(),anyList())).thenReturn(new ExpenseChatModel.Reply("How much?",List.of(),new ExpenseChatModel.Usage(100,0,50)));
        assertThatThrownBy(()->service.capture(user,request("Groceries",List.of()))).isInstanceOf(WebApiException.class);
        verify(model,times(3)).complete(anyString(),anyList(),anyList());
        verify(credits,times(3)).settle(nullable(UUID.class),any());
        verifyNoInteractions(store);
    }
    @Test void terminalPreparationCannotBeMixedWithReadsOrMultiplePreviews() {
        when(model.complete(anyString(),anyList(),anyList())).thenReturn(new ExpenseChatModel.Reply(null,
                List.of(new ExpenseChatModel.Call("capture","prepare_expense",facts("450","null")),
                        new ExpenseChatModel.Call("lookup","query_expenses","{}")),new ExpenseChatModel.Usage(100,0,50)));
        assertThatThrownBy(()->service.capture(user,request("Paid 450",List.of()))).isInstanceOf(WebApiException.class);
        verifyNoInteractions(store);
        verify(tools,never()).callResult(any(),anyString(),anyString());
    }
    @Test void failedReadReturnsToolErrorForRepairWithoutInferringFinancialFacts() {
        var failure=Map.<String,Object>of("isError",true,"content",List.of());
        when(tools.callResult(user,"query_expenses","{}")).thenReturn(failure);
        when(tools.json(failure)).thenReturn("{\"isError\":true}");
        when(model.complete(anyString(),anyList(),anyList())).thenReturn(
                new ExpenseChatModel.Reply(null,List.of(new ExpenseChatModel.Call("lookup","query_expenses","{}")),new ExpenseChatModel.Usage(100,0,50)),
                toolReply(facts("null","null")));
        assertThat(service.capture(user,request("Bought groceries",List.of())).status()).isEqualTo("NEEDS_DETAILS");
        verify(store,never()).save(any(),any(),any(),anyString(),eq(true));
    }

    @Test void latestExplicitAccountCorrectionWinsOverEarlierGenericWording() {
        when(refs.findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.ACCOUNT))
                .thenReturn(List.of(reference(1,"ICICI credit card"),reference(2,"HDFC bank account")));
        reply(facts("450","null").replace("\"account\":null","\"account\":\"HDFC bank account\""));
        assertThat(service.capture(user,request("Actually used HDFC bank account",List.of("Paid 450 using credit card for groceries")))
                .preview().account()).isEqualTo("HDFC bank account");
    }
    @Test void extremeAmountsAndStorageOverflowCannotBePrepared() {
        for(String amount:List.of("1e100","1e-100","99999999999999999.995","-10")) {
            reply(facts(amount,"null"));
            assertThat(service.capture(user,request("Paid for groceries",List.of())).status()).isEqualTo("NEEDS_DETAILS");
        }
        verify(store,never()).save(any(),any(),any(),anyString(),eq(true));
    }
    @Test void toolCallBudgetStopsLookupsBeforeExceedingSixCalls() {
        var calls=List.of(new ExpenseChatModel.Call("one","query_expenses","{}"),
                new ExpenseChatModel.Call("two","query_expenses","{}"),new ExpenseChatModel.Call("three","query_expenses","{}"));
        when(tools.json(any())).thenReturn("{}");
        when(model.complete(anyString(),anyList(),anyList())).thenReturn(new ExpenseChatModel.Reply(null,calls,new ExpenseChatModel.Usage(100,0,50)));
        assertThatThrownBy(()->service.capture(user,request("Paid 450",List.of()))).isInstanceOf(WebApiException.class);
        verify(tools,times(6)).callResult(user,"query_expenses","{}");
        verifyNoInteractions(store);
    }

    @Test void parserRejectsExtremeExponentsWithoutWritingAnyDraft() {
        for(String amount:List.of("1e10000","1e-10000")) {
            reply(facts(amount,"null"));
            assertThatThrownBy(()->service.capture(user,request("Paid for groceries",List.of())))
                    .isInstanceOf(WebApiException.class);
        }
        verifyNoInteractions(store);
    }

    @Test void unsupportedUpdatesUseSamePortalInstructionForWebAndWhatsAppWithoutConfirmation() {
        when(store.save(eq(user),any(),any(),anyString(),eq(false))).thenReturn(null);
        for(String message:List.of("Add a loan for 25000", "Record a SIP of 5000", "Update my income to 75000", "Delete yesterday's expense")) {
            reply(facts("null","null").replace("\"eligible\":true","\"eligible\":false"));
            var web=service.capture(user,request(message,List.of()));
            var whatsapp=service.prepare(user,LocalDate.parse("2026-10-01"),List.of(message));
            assertThat(web.answer()).isEqualTo(WebExpenseCaptureService.PORTAL_UPDATE);
            assertThat(WebExpenseCaptureService.captureInstruction(whatsapp)).isEqualTo(web.answer());
            assertThat(web.extractionId()).isNull();
            assertThat(web.status()).isEqualTo("NEEDS_DETAILS");
        }
        verify(store,never()).save(any(),any(),any(),anyString(),eq(true));
    }
}
