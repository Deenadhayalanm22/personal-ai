package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.LoanStatus;
import com.apps.deen_sa.domain.LoanType;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserLoanEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.UserLoanRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WebLoanServiceTest {

    @Test
    void createsActiveLoanAndRoundsMoneyAmounts() {
        UserLoanRepository repository = mock(UserLoanRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> {
            UserLoanEntity loan = invocation.getArgument(0);
            loan.setId(7L);
            return loan;
        });
        AppUserEntity user = user();

        var result = new WebLoanService(repository).create(user, new WebLoanService.LoanCreateRequest(
                "  HDFC home loan ", LoanType.HOME, " HDFC Bank ", new BigDecimal("5000000.005"),
                new BigDecimal("45000.444"), 240, LocalDate.of(2025, 4, 5), null, "  Main home  "));

        ArgumentCaptor<UserLoanEntity> saved = ArgumentCaptor.forClass(UserLoanEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(result).extracting(WebLoanService.LoanResponse::id, WebLoanService.LoanResponse::loanName,
                WebLoanService.LoanResponse::originalPrincipal, WebLoanService.LoanResponse::monthlyEmiAmount,
                WebLoanService.LoanResponse::status)
                .containsExactly(7L, "HDFC home loan", new BigDecimal("5000000.01"),
                        new BigDecimal("45000.44"), LoanStatus.ACTIVE);
    }

    @Test
    void rejectsInvalidLoanAmounts() {
        UserLoanRepository repository = mock(UserLoanRepository.class);
        var request = new WebLoanService.LoanCreateRequest("Personal loan", LoanType.PERSONAL, "Bank",
                BigDecimal.ZERO, new BigDecimal("5000"), 12, LocalDate.of(2026, 1, 1), null, null);

        assertThatThrownBy(() -> new WebLoanService(repository).create(user(), request))
                .isInstanceOf(WebApiException.class)
                .extracting(error -> ((WebApiException) error).code())
                .isEqualTo("INVALID_LOAN");
        verifyNoInteractions(repository);
    }

    @Test
    void listsOnlyTheAuthenticatedUsersLoansAndUpdatesOwnedLoan() {
        UserLoanRepository repository = mock(UserLoanRepository.class);
        AppUserEntity user = user();
        UserLoanEntity loan = loan(user);
        when(repository.findByUserIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(loan));
        when(repository.findByIdAndUserId(8L, 42L)).thenReturn(Optional.of(loan));
        when(repository.save(loan)).thenReturn(loan);
        WebLoanService service = new WebLoanService(repository);

        assertThat(service.list(user).loans()).singleElement().extracting(WebLoanService.LoanResponse::id)
                .isEqualTo(8L);
        var updated = service.update(user, 8L, new WebLoanService.LoanUpdateRequest(
                null, null, null, null, new BigDecimal("12000"), null, null, "EMI revised"));

        verify(repository).findByIdAndUserId(8L, 42L);
        assertThat(updated.monthlyEmiAmount()).isEqualByComparingTo("12000.00");
        assertThat(updated.status()).isEqualTo(LoanStatus.ACTIVE);
    }

    private AppUserEntity user() {
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        return user;
    }

    private UserLoanEntity loan(AppUserEntity user) {
        UserLoanEntity loan = new UserLoanEntity();
        loan.setId(8L);
        loan.setUser(user);
        loan.setLoanName("Car loan");
        loan.setLoanType(LoanType.VEHICLE);
        loan.setLenderName("ICICI Bank");
        loan.setOriginalPrincipal(new BigDecimal("700000.00"));
        loan.setMonthlyEmiAmount(new BigDecimal("15000.00"));
        loan.setTotalTenureMonths(60);
        loan.setFirstEmiDueDate(LocalDate.of(2026, 1, 5));
        return loan;
    }
}
