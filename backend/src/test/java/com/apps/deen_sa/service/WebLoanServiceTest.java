package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.LoanStatus;
import com.apps.deen_sa.domain.LoanType;
import com.apps.deen_sa.domain.LoanEmiOccurrenceStatus;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.LoanEmiOccurrenceEntity;
import com.apps.deen_sa.entity.UserLoanEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.UserLoanRepository;
import com.apps.deen_sa.repository.LoanEmiOccurrenceRepository;
import com.apps.deen_sa.repository.UserActionItemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WebLoanServiceTest {

    @Test
    void loanProgressAdvancesOnlyWhenDueEmiIsPaid() {
        UserLoanRepository repository = mock(UserLoanRepository.class);
        LoanEmiOccurrenceRepository occurrences = mock(LoanEmiOccurrenceRepository.class);
        AppUserEntity owner = user();
        owner.setTimezone("Asia/Kolkata");
        UserLoanEntity loan = loan(owner);
        loan.setTotalTenureMonths(6);
        loan.setFirstEmiDueDate(LocalDate.of(2026, 1, 1));
        List<LoanEmiOccurrenceEntity> records = new java.util.ArrayList<>();
        for (int month = 1; month <= 4; month++) {
            LoanEmiOccurrenceEntity paid = new LoanEmiOccurrenceEntity();
            paid.setDueMonth(LocalDate.of(2026, month, 1));
            paid.setDueDate(paid.getDueMonth());
            paid.setStatus(LoanEmiOccurrenceStatus.PAID);
            records.add(paid);
        }
        when(repository.findByUserIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(loan));
        when(repository.findByIdAndUserId(8L, 42L)).thenReturn(Optional.of(loan));
        when(occurrences.findByLoanIdOrderByDueMonthAsc(8L)).thenAnswer(invocation -> records);
        when(occurrences.findByLoanIdAndDueMonth(8L, LocalDate.of(2026, 5, 1))).thenReturn(Optional.empty());
        when(occurrences.save(any())).thenAnswer(invocation -> {
            LoanEmiOccurrenceEntity saved = invocation.getArgument(0);
            records.add(saved);
            return saved;
        });
        WebLoanService service = new WebLoanService(repository, null,
                Clock.fixed(Instant.parse("2026-05-01T09:00:00Z"), ZoneId.of("Asia/Kolkata")), occurrences, null);

        assertThat(service.list(owner).loans().get(0).completedEmiCount()).isEqualTo(4);
        assertThat(service.markPaid(owner, 8L, YearMonth.of(2026, 5)).completedEmiCount()).isEqualTo(5);
    }

    @Test
    void managesLoanLifecycle() {
        UserLoanRepository repository = mock(UserLoanRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> {
            UserLoanEntity loan = invocation.getArgument(0);
            loan.setId(7L);
            return loan;
        });
        AppUserEntity createdUser = user();

        var result = new WebLoanService(repository).create(createdUser, new WebLoanService.LoanCreateRequest(
                "  HDFC home loan ", LoanType.HOME, " HDFC Bank ", new BigDecimal("5000000.005"),
                new BigDecimal("45000.444"), 240, LocalDate.of(2025, 4, 5), null, "  Main home  "));

        ArgumentCaptor<UserLoanEntity> saved = ArgumentCaptor.forClass(UserLoanEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(createdUser);
        assertThat(result).extracting(WebLoanService.LoanResponse::id, WebLoanService.LoanResponse::loanName,
                WebLoanService.LoanResponse::originalPrincipal, WebLoanService.LoanResponse::monthlyEmiAmount,
                WebLoanService.LoanResponse::status)
                .containsExactly(7L, "HDFC home loan", new BigDecimal("5000000.01"),
                        new BigDecimal("45000.44"), LoanStatus.ACTIVE);

        UserLoanRepository invalidRepository = mock(UserLoanRepository.class);
        var request = new WebLoanService.LoanCreateRequest("Personal loan", LoanType.PERSONAL, "Bank",
                BigDecimal.ZERO, new BigDecimal("5000"), 12, LocalDate.of(2026, 1, 1), null, null);

        assertThatThrownBy(() -> new WebLoanService(invalidRepository).create(user(), request))
                .isInstanceOf(WebApiException.class)
                .extracting(error -> ((WebApiException) error).code())
                .isEqualTo("INVALID_LOAN");
        verifyNoInteractions(invalidRepository);

        UserLoanRepository ownedRepository = mock(UserLoanRepository.class);
        AppUserEntity ownedUser = user();
        UserLoanEntity ownedLoan = loan(ownedUser);
        when(ownedRepository.findByUserIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(ownedLoan));
        when(ownedRepository.findByIdAndUserId(8L, 42L)).thenReturn(Optional.of(ownedLoan));
        when(ownedRepository.save(ownedLoan)).thenReturn(ownedLoan);
        WebLoanService service = new WebLoanService(ownedRepository);

        assertThat(service.list(ownedUser).loans()).singleElement().extracting(WebLoanService.LoanResponse::id)
                .isEqualTo(8L);
        var updated = service.update(ownedUser, 8L, new WebLoanService.LoanUpdateRequest(
                null, null, null, null, new BigDecimal("12000"), null, null, "EMI revised"));

        verify(ownedRepository).findByIdAndUserId(8L, 42L);
        assertThat(updated.monthlyEmiAmount()).isEqualByComparingTo("12000.00");
        assertThat(updated.status()).isEqualTo(LoanStatus.ACTIVE);
        UserLoanRepository finalEmiRepository = mock(UserLoanRepository.class);
        LoanEmiOccurrenceRepository occurrences = mock(LoanEmiOccurrenceRepository.class);
        UserActionItemRepository actions = mock(UserActionItemRepository.class);
        AppUserEntity finalEmiUser = user();
        finalEmiUser.setTimezone("Asia/Kolkata");
        UserLoanEntity finalEmiLoan = loan(finalEmiUser);
        finalEmiLoan.setTotalTenureMonths(1);
        finalEmiLoan.setFirstEmiDueDate(LocalDate.of(2026, 6, 1));
        when(finalEmiRepository.findByIdAndUserId(8L, 42L)).thenReturn(Optional.of(finalEmiLoan));
        when(occurrences.findByLoanIdAndDueMonth(8L, LocalDate.of(2026, 6, 1))).thenReturn(Optional.empty());
        when(occurrences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(occurrences.findByLoanIdOrderByDueMonthAsc(8L)).thenAnswer(invocation -> {
            LoanEmiOccurrenceEntity paid = new LoanEmiOccurrenceEntity();
            paid.setDueMonth(LocalDate.of(2026, 6, 1));
            paid.setDueDate(paid.getDueMonth());
            paid.setStatus(LoanEmiOccurrenceStatus.PAID);
            return List.of(paid);
        });
        when(finalEmiRepository.save(finalEmiLoan)).thenReturn(finalEmiLoan);
        WebLoanService finalEmiService = new WebLoanService(finalEmiRepository, null,
                Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("Asia/Kolkata")), occurrences, actions);

        var paidResult = finalEmiService.markPaid(finalEmiUser, 8L, YearMonth.of(2026, 6));

        assertThat(paidResult.status()).isEqualTo(LoanStatus.CLOSED);
        assertThat(paidResult.completedEmiCount()).isEqualTo(1);
        assertThat(paidResult.remainingEmiCount()).isZero();
        verify(actions).deleteByUserIdAndReferenceTypeAndReferenceId(42L, "LOAN", 8L);
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
