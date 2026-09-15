package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.InvestmentAssetType;
import com.apps.deen_sa.domain.InvestmentSipStatus;
import com.apps.deen_sa.domain.LoanStatus;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserInvestmentEntity;
import com.apps.deen_sa.entity.UserLoanEntity;
import com.apps.deen_sa.repository.UserInvestmentRepository;
import com.apps.deen_sa.repository.UserLoanRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonthlyCommitmentStoryServiceTest {
    @Test
    void totalsOnlyCommitmentsActiveInTheCurrentMonth() {
        UserLoanRepository loans = mock(UserLoanRepository.class);
        UserInvestmentRepository investments = mock(UserInvestmentRepository.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(7L); user.setCurrency("INR"); user.setTimezone("Asia/Kolkata");
        when(loans.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(
                loan("Home loan", "26000", LocalDate.of(2026, 1, 5), 240, LoanStatus.ACTIVE),
                loan("Finished loan", "5000", LocalDate.of(2025, 1, 5), 12, LoanStatus.ACTIVE),
                loan("Future loan", "9000", LocalDate.of(2026, 11, 5), 12, LoanStatus.ACTIVE),
                loan("Closed loan", "7000", LocalDate.of(2026, 1, 5), 240, LoanStatus.CLOSED)));
        when(investments.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(
                sip("20000", LocalDate.of(2026, 1, 1), InvestmentSipStatus.ACTIVE),
                sip("3000", LocalDate.of(2026, 11, 1), InvestmentSipStatus.ACTIVE),
                sip("4000", LocalDate.of(2026, 1, 1), InvestmentSipStatus.PAUSED)));

        var story = new MonthlyCommitmentStoryService(loans, investments,
                Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneId.of("Asia/Kolkata"))).currentFor(user);

        assertThat(story.storyType()).isEqualTo("MONTHLY_COMMITMENT");
        assertThat(story.storyId()).isEqualTo("monthly-commitment");
        assertThat(story.cardFace().displayValue()).contains("46,000");
        assertThat(story.cards().getFirst().components()).extracting(component -> component.label())
                .containsExactly("Loan EMIs", "Mutual fund SIPs");
    }

    private UserLoanEntity loan(String name, String amount, LocalDate firstDue, int tenure, LoanStatus status) {
        UserLoanEntity loan = new UserLoanEntity();
        loan.setLoanName(name); loan.setMonthlyEmiAmount(new BigDecimal(amount)); loan.setFirstEmiDueDate(firstDue);
        loan.setTotalTenureMonths(tenure); loan.setStatus(status); return loan;
    }

    private UserInvestmentEntity sip(String amount, LocalDate start, InvestmentSipStatus status) {
        UserInvestmentEntity investment = new UserInvestmentEntity();
        investment.setAssetType(InvestmentAssetType.MUTUAL_FUND); investment.setSipAmount(new BigDecimal(amount));
        investment.setSipStartMonth(start); investment.setSipStatus(status); return investment;
    }
}
