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
    final WebExpenseCaptureService service=new WebExpenseCaptureService(model,credits,policy,mapper,taxonomy,refs,aliases,store,
            Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"),ZoneOffset.UTC));
    WebExpenseCaptureServiceTest(){user.setId(42L);user.setChannel("WEB_DEMO");user.setExternalUserId("demo");}
    WebExpenseCaptureService.Request request(String message,List<String> turns){return new WebExpenseCaptureService.Request(UUID.randomUUID(),LocalDate.parse("2026-09-28"),message,turns);}
    void reply(String json){when(model.complete(anyString(),anyList(),anyList())).thenReturn(new ExpenseChatModel.Reply(json,List.of(),new ExpenseChatModel.Usage(100,0,50)));}
    String facts(String amount,String date){String category=taxonomy.categories().iterator().next(),sub=taxonomy.subcategoriesFor(category).iterator().next();return "{\"amount\":"+amount+",\"date\":"+date+",\"category\":\""+category+"\",\"subcategory\":\""+sub+"\",\"merchant\":\"SB\",\"account\":null,\"clarification\":null}";}
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
        verify(model).complete(contains("Today: 2026-10-01"),anyList(),eq(List.of()));
        verify(refs,atLeastOnce()).findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.MERCHANT);
        verify(credits).settle(nullable(UUID.class),eq(new ExpenseChatModel.Usage(100,0,50)));
    }
    @Test void missingAmountAsksFollowupAndRetainsPriorUserStatements(){
        reply(facts("null","null"));
        var response=service.capture(user,request("For groceries",List.of("Bought groceries at SB")));
        assertThat(response.status()).isEqualTo("NEEDS_DETAILS");assertThat(response.answer()).isEqualTo("How much did you pay?");
        verify(store).save(eq(user),any(),any(),contains("Bought groceries at SB"),eq(false));
        verify(model).complete(anyString(),argThat(messages->messages.size()==2 && messages.get(0).content().equals("Bought groceries at SB")),anyList());
    }
    @Test void invalidTaxonomyAndRoundedZeroNeverProduceConfirmablePreview(){
        reply(facts("0.001","null"));
        assertThat(service.capture(user,request("Paid a tiny amount",List.of())).status()).isEqualTo("NEEDS_DETAILS");
        reply("{\"amount\":450,\"category\":\"Unknown\",\"subcategory\":\"Unknown\"}");
        assertThat(service.capture(user,request("Paid 450",List.of())).status()).isEqualTo("NEEDS_DETAILS");
        verify(store,never()).save(any(),any(),any(),anyString(),eq(true));
    }
    @Test void explicitDateIsPreviewedAndFutureDateNeedsClarification(){
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
}
