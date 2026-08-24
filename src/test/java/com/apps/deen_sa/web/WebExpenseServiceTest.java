package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.expense.ExpenseRecordStatus;
import com.apps.deen_sa.finance.expense.correction.ExpenseCorrectionService;
import com.apps.deen_sa.finance.legacy.state.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebExpenseServiceTest {
    private final StateChangeRepository expenses = mock(StateChangeRepository.class);
    private final StateContainerRepository accounts = mock(StateContainerRepository.class);
    private final ExpenseCorrectionService corrections = mock(ExpenseCorrectionService.class);
    private final WebExpenseService service = new WebExpenseService(expenses, accounts, corrections);
    private final AppUserEntity user = new AppUserEntity();

    @BeforeEach
    void setUp() {
        user.setId(42L); user.setTimezone("Asia/Kolkata"); user.setCurrency("INR");
    }

    @Test
    void listsOnlyTheAuthenticatedUsersMonthWithCursorPagination() {
        StateChangeEntity first = expense(12L, "Food", 1);
        StateChangeEntity second = expense(11L, "Travel", 2);
        when(expenses.findActiveExpensesForPeriodBefore(eq("42"), any(), any(), isNull(), any()))
                .thenReturn(List.of(first, second));

        var page = service.list(user, YearMonth.of(2026, 8), 1, null);

        assertThat(page.items()).extracting(WebExpenseService.ExpenseItem::id).containsExactly(12L);
        assertThat(page.nextBeforeId()).isEqualTo(12L);
        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(expenses).findActiveExpensesForPeriodBefore(eq("42"), start.capture(), end.capture(),
                isNull(), pageable.capture());
        assertThat(start.getValue()).isEqualTo(Instant.parse("2026-07-31T18:30:00Z"));
        assertThat(end.getValue()).isEqualTo(Instant.parse("2026-08-31T18:30:00Z"));
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    void classificationEditUsesAuthenticatedUserAndExpectedVersion() {
        StateChangeEntity replacement = expense(15L, "Food", 4);
        replacement.setSubcategory("Groceries");
        when(corrections.editClassification(42L, 12L, 3, "Food", "Groceries"))
                .thenReturn(replacement);

        var result = service.editClassification(user, 12L,
                new WebExpenseService.ClassificationUpdate(" Food ", " Groceries ", 3));

        assertThat(result.id()).isEqualTo(15L);
        assertThat(result.version()).isEqualTo(4);
        verify(corrections).editClassification(42L, 12L, 3, "Food", "Groceries");
    }

    @Test
    void rejectsBlankClassification() {
        assertThatThrownBy(() -> service.editClassification(user, 12L,
                new WebExpenseService.ClassificationUpdate(" ", null, 1)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400 BAD_REQUEST");
        verifyNoInteractions(corrections);
    }

    private StateChangeEntity expense(Long id, String category, int version) {
        StateChangeEntity value = new StateChangeEntity();
        value.setId(id); value.setUserId("42"); value.setAmount(new BigDecimal("100"));
        value.setTimestamp(Instant.parse("2026-08-12T08:00:00Z")); value.setCategory(category);
        value.setRecordStatus(ExpenseRecordStatus.ACTIVE); value.setRecordVersion(version);
        value.setTransactionType(StateChangeTypeEnum.EXPENSE); value.setRawText("Paid 100");
        return value;
    }
}
