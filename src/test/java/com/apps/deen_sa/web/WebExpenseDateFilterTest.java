package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.expense.correction.ExpenseCorrectionService;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.tag.TagRepository;
import com.apps.deen_sa.finance.tag.TransactionTagRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebExpenseDateFilterTest {

    @Test
    void limitsItemsAndSummaryToSelectedLocalDate() {
        StateChangeRepository repository = mock(StateChangeRepository.class);
        when(repository.findFilteredActiveExpensesBefore(anyString(), any(), any(), anyBoolean(), anyString(),
                anyBoolean(), anyString(), any(), any())).thenReturn(List.of());
        when(repository.summarizeFilteredActiveExpenses(anyString(), any(), any(), anyBoolean(), anyString(),
                anyBoolean(), anyString())).thenReturn(List.<Object[]>of(new Object[]{0L, BigDecimal.ZERO}));
        WebExpenseService service = new WebExpenseService(repository, mock(ExpenseCorrectionService.class),
                mock(WebExpenseTaxonomyService.class), mock(TagRepository.class),
                mock(TransactionTagRepository.class));
        AppUserEntity user = new AppUserEntity();
        user.setId(42L); user.setTimezone("Asia/Kolkata"); user.setCurrency("INR");

        var response = service.list(user, YearMonth.of(2026, 8), 50, null,
                new WebExpenseService.ExpenseFilter(null, null, List.of(),
                        WebExpenseService.TagMatch.ANY, LocalDate.of(2026, 8, 27)));

        Instant start = Instant.parse("2026-08-26T18:30:00Z");
        Instant end = Instant.parse("2026-08-27T18:30:00Z");
        verify(repository).findFilteredActiveExpensesBefore(eq("42"), eq(start), eq(end), eq(false), eq(""),
                eq(false), eq(""), isNull(), any());
        verify(repository).summarizeFilteredActiveExpenses(eq("42"), eq(start), eq(end), eq(false), eq(""),
                eq(false), eq(""));
        assertThat(response.items()).isEmpty();
    }
}
