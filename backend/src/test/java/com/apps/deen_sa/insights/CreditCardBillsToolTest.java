package com.apps.deen_sa.insights;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.service.CreditCardBillService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreditCardBillsToolTest {
    private final CreditCardBillService bills = mock(CreditCardBillService.class);
    private final CreditCardBillsTool tool = new CreditCardBillsTool(bills);
    private final AppUserEntity user = new AppUserEntity();
    private CreditCardBillService.Bill bill(long id) {
        return new CreditCardBillService.Bill(id, "Card " + id, "2026-06", LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-31"), LocalDate.parse("2026-06-21"), new BigDecimal("4000"),
                new BigDecimal("4500"), BigDecimal.ZERO, true, List.of(), LocalDate.parse("2026-06-01"),
                new BigDecimal("900"), new BigDecimal("500"));
    }
    @Test void preservesCanonicalBillAmountsAndPeriodsWithoutAddingPaymentsToSpending() {
        when(bills.summaries(user, YearMonth.of(2026,6),1,0)).thenReturn(new CreditCardBillService.SummaryList(2, List.of(bill(1))));
        when(bills.summaries(user, YearMonth.of(2026,6),1,1)).thenReturn(new CreditCardBillService.SummaryList(2, List.of(bill(2))));
        var result = tool.read(user, new CreditCardBillsTool.Request("2026-06",1,0));
        assertThat(result.matchingCount()).isEqualTo(2);
        assertThat(result.truncated()).isTrue(); assertThat(result.nextOffset()).isEqualTo(1);
        assertThat(result.rows().getFirst()).containsEntry("captured_bill_amount", new BigDecimal("4000"))
                .containsEntry("paid_amount", new BigDecimal("4500"))
                .containsEntry("monthly_purchase_amount", new BigDecimal("900"))
                .containsEntry("remaining_amount", BigDecimal.ZERO)
                .containsEntry("unmatched_payment_amount", new BigDecimal("500"))
                .containsEntry("statement_generated_at", LocalDate.parse("2026-06-01"));
        var next = tool.read(user, new CreditCardBillsTool.Request("2026-06",1,1));
        assertThat(next.rows().getFirst()).containsEntry("card_id",2L);
        assertThat(next.truncated()).isFalse(); assertThat(next.nextOffset()).isNull();
        verify(bills).summaries(user,YearMonth.of(2026,6),1,0);
        verify(bills).summaries(user,YearMonth.of(2026,6),1,1);
    }
    @Test void rejectsInvalidRequestsBeforeReadingAnyFinancialData() {
        for (var request : List.of(new CreditCardBillsTool.Request("bad",10,0),
                new CreditCardBillsTool.Request("2026-06",51,0), new CreditCardBillsTool.Request("2026-06",10,-1)))
            assertThatThrownBy(() -> tool.read(user,request)).isInstanceOf(com.apps.deen_sa.exception.WebApiException.class);
        verifyNoInteractions(bills);
    }
    @Test void catalogRegistersTheNewToolAndRejectsOwnershipOverrides() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var catalog = new ExpenseMcpTools(mock(ExpenseQueryTool.class), mock(FinancialRecordsTool.class), mock(MonthlyPlanningTool.class),tool,mapper);
        assertThat(catalog.definitions()).extracting(node -> node.path("name").asText()).contains("read_credit_card_bills");
        assertThatThrownBy(() -> catalog.call(user,"read_credit_card_bills","{\"month\":\"2026-06\",\"limit\":10,\"offset\":0,\"owner\":123}"))
                .isInstanceOf(com.apps.deen_sa.exception.WebApiException.class);
        verifyNoInteractions(bills);
    }
}
