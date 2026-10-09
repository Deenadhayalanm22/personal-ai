package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.repository.*;
import com.apps.deen_sa.exception.WebApiException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommitmentPaymentServiceTest {
    final FinancialTransactionRepository transactions = mock(FinancialTransactionRepository.class);
    final UserReferenceEntityRepository references = mock(UserReferenceEntityRepository.class);
    final ExpenseDailyAggregateRepository aggregates = mock(ExpenseDailyAggregateRepository.class);
    final CommitmentPaymentService service = new CommitmentPaymentService(transactions,
            mock(RecurringCommitmentOccurrenceRepository.class), mock(RecurringCommitmentExtraRepository.class),
            aggregates, mock(ExpenseTaxonomyRegistry.class), Clock.systemUTC(), references);
    final LocalDate date = LocalDate.of(2026, 10, 9);
    UserRecurringCommitmentEntity commitment() {
        var user = new AppUserEntity(); user.setId(1L);
        var commitment = new UserRecurringCommitmentEntity(); commitment.setId(2L); commitment.setUser(user); return commitment;
    }
    @Test void newPaymentStoresValidatedAccountAndRebuildsPurchaseDate() {
        var account = new UserReferenceEntity(); account.setId(3L);
        when(references.findByIdAndUserIdAndEntityTypeAndActiveTrue(3L, 1L, UserReferenceEntityType.ACCOUNT)).thenReturn(Optional.of(account));
        var payment = service.record(commitment(), new BigDecimal("900"), date, null, "occurrence:1", "Internet", 3L);
        assertThat(payment.getSourceAccount()).isSameAs(account);
        assertThat(payment.getAmount()).isEqualByComparingTo("900");
        verify(transactions).saveAndFlush(payment); verify(aggregates).markDateForRebuild(date);
    }
    @Test void unavailableAccountFailsBeforeAnyExpenseWrite() {
        assertThatThrownBy(() -> service.record(commitment(), new BigDecimal("900"), date, null, "occurrence:1", "Internet", 3L)).isInstanceOf(WebApiException.class);
        verifyNoInteractions(transactions, aggregates);
    }
    @Test void attachmentKeepsExistingAccountAndRejectsDifferentAccount() {
        var account = new UserReferenceEntity(); account.setId(3L);
        var expense = new FinancialTransactionEntity(); expense.setId(4L); expense.setAmount(new BigDecimal("900")); expense.setOccurredAt(date); expense.setSourceAccount(account);
        when(transactions.findOwnedForUpdate(4L, 1L)).thenReturn(Optional.of(expense));
        assertThat(service.record(commitment(), new BigDecimal("900"), date, 4L, "occurrence:1", "Internet").getSourceAccount()).isSameAs(account);
        var different = new UserReferenceEntity(); different.setId(5L);
        when(references.findByIdAndUserIdAndEntityTypeAndActiveTrue(5L, 1L, UserReferenceEntityType.ACCOUNT)).thenReturn(Optional.of(different));
        assertThatThrownBy(() -> service.record(commitment(), new BigDecimal("900"), date, 4L, "occurrence:1", "Internet", 5L)).isInstanceOf(WebApiException.class).hasMessageContaining("different source account");
    }
}
