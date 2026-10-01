package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ManualExpenseCaptureServiceTest {
    final TransactionDraftRepository drafts=mock(TransactionDraftRepository.class);
    final TransactionDraftExtractionRepository extractions=mock(TransactionDraftExtractionRepository.class);
    final UserReferenceEntityRepository refs=mock(UserReferenceEntityRepository.class);
    final UserReferenceAliasRepository aliases=mock(UserReferenceAliasRepository.class);
    final ExpenseTaxonomyRegistry taxonomy=new ExpenseTaxonomyRegistry();
    final AppUserEntity user=new AppUserEntity();
    final ManualExpenseCaptureService service=new ManualExpenseCaptureService(drafts,extractions,taxonomy,refs,aliases,
            Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"),ZoneOffset.UTC));
    ManualExpenseCaptureServiceTest(){user.setId(42L);}
    ManualExpenseCaptureService.Request request(String amount,LocalDate date,String category,String sub){
        return new ManualExpenseCaptureService.Request(UUID.randomUUID(),date,new BigDecimal(amount),category,sub,null,null);
    }
    @Test void rejectsMissingInvalidOrFutureFactsBeforeCreatingAnyDraft(){
        var category=taxonomy.categories().iterator().next();var sub=taxonomy.subcategoriesFor(category).iterator().next();
        for(var request:List.of(request("0.001",LocalDate.parse("2026-09-28"),category,sub),
                request("10",LocalDate.parse("2026-10-02"),category,sub),request("10",LocalDate.parse("2026-09-28"),"unknown","unknown"),
                request("100000000000000000",LocalDate.parse("2026-09-28"),category,sub)))
            assertThatThrownBy(()->service.prepare(user,request)).isInstanceOf(WebApiException.class);
        assertThatThrownBy(()->service.prepare(user,null)).isInstanceOf(WebApiException.class);
        verifyNoInteractions(drafts,extractions,refs,aliases);
    }
    @Test void resolvesOnlyOwnedActiveAliasesWithoutCreatingNamesOrTransactions() {
        var category=taxonomy.categories().iterator().next();var sub=taxonomy.subcategoriesFor(category).iterator().next();
        var draft=new TransactionDraftEntity();draft.setId(8L);
        when(drafts.insertPendingIfAbsent(eq(42L),eq("TEXT"),eq("WEB_APP"),anyString(),anyString())).thenAnswer(call->{draft.setRawText(call.getArgument(4));return 1;});
        when(drafts.findBySourceAndSourceMessageId(eq(MessageSource.WEB_APP),anyString())).thenReturn(Optional.of(draft));
        when(drafts.findByIdForUpdate(8L)).thenReturn(Optional.of(draft));
        when(extractions.saveAndFlush(any())).thenAnswer(call->{TransactionDraftExtractionEntity value=call.getArgument(0);value.setId(123L);return value;});
        var ref=new UserReferenceEntity();ref.setId(9L);ref.setCanonicalName("Saved shop");
        var alias=new UserReferenceAliasEntity();alias.setAliasText("SS");
        when(refs.findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.MERCHANT)).thenReturn(List.of(ref));
        when(aliases.findByReferenceEntityId(9L)).thenReturn(List.of(alias));
        var request=new ManualExpenseCaptureService.Request(UUID.randomUUID(),LocalDate.parse("2026-09-28"),new BigDecimal("10.129"),category,sub," SS ","New bank account");
        var result=service.prepare(user,request);
        assertThat(result.preview().merchant()).isEqualTo("Saved shop");
        assertThat(result.preview().account()).isEqualTo("New bank account");
        assertThat(result.preview().amount()).isEqualByComparingTo("10.13");
        verify(refs,never()).save(any());verify(aliases,never()).save(any());
        var second=new UserReferenceEntity();second.setId(10L);second.setCanonicalName("Other shop");
        when(refs.findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.MERCHANT)).thenReturn(List.of(ref,second));
        when(aliases.findByReferenceEntityId(10L)).thenReturn(List.of(alias));
        assertThatThrownBy(()->service.prepare(user,request)).isInstanceOf(WebApiException.class)
                .extracting("code").isEqualTo("CAPTURE_AMBIGUOUS_REFERENCE");
        var canonical=new ManualExpenseCaptureService.Request(UUID.randomUUID(),request.date(),request.amount(),category,sub,"Saved shop",null);
        assertThat(service.prepare(user,canonical).preview().merchant()).isEqualTo("Saved shop");
    }
    @Test void usesProfileLocalTodayAtTimezoneBoundary(){
        user.setTimezone("America/Los_Angeles");
        var category=taxonomy.categories().iterator().next();var sub=taxonomy.subcategoriesFor(category).iterator().next();
        assertThatThrownBy(()->service.prepare(user,request("10",LocalDate.parse("2026-10-01"),category,sub)))
                .isInstanceOf(WebApiException.class);
        verifyNoInteractions(drafts);
    }
}
