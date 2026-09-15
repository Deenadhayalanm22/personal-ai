package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.InvestmentAssetType;
import com.apps.deen_sa.domain.InvestmentSipStatus;
import com.apps.deen_sa.domain.LoanStatus;
import com.apps.deen_sa.domain.MoneyStoryLevel;
import com.apps.deen_sa.domain.MoneyStoryType;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserInvestmentEntity;
import com.apps.deen_sa.entity.UserLoanEntity;
import com.apps.deen_sa.repository.UserInvestmentRepository;
import com.apps.deen_sa.repository.UserLoanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces the always-current planning anchor in the existing Stories response.
 * It is deliberately not snapshot-backed: changing a loan or SIP is visible on the next read.
 */
@Service
public class MonthlyCommitmentStoryService {
    private final UserLoanRepository loans;
    private final UserInvestmentRepository investments;
    private final Clock clock;

    public MonthlyCommitmentStoryService(UserLoanRepository loans, UserInvestmentRepository investments, Clock clock) {
        this.loans = loans;
        this.investments = investments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MoneyStoriesService.MoneyStoryApi currentFor(AppUserEntity user) {
        YearMonth month = YearMonth.now(clock.withZone(java.time.ZoneId.of(user.getTimezone())));
        BigDecimal emiTotal = loans.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(loan -> hasEmiIn(loan, month))
                .map(UserLoanEntity::getMonthlyEmiAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sipTotal = investments.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(investment -> hasSipIn(investment, month))
                .map(UserInvestmentEntity::getSipAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = emiTotal.add(sipTotal);
        String currency = user.getCurrency();
        String value = MoneyStoryRenderer.money(total, currency);
        List<MoneyStoriesService.Component> components = new ArrayList<>();
        if (emiTotal.signum() > 0) components.add(component("Loan EMIs", emiTotal, currency));
        if (sipTotal.signum() > 0) components.add(component("Mutual fund SIPs", sipTotal, currency));

        String title = total.signum() > 0 ? "Your known monthly commitment" : "Your monthly commitment starts here";
        String body = total.signum() > 0
                ? "Set aside " + value + " for active loan EMIs and mutual fund SIPs this month."
                : "Add a loan or a mutual fund SIP and it will appear here as part of your monthly commitment.";
        var card = new MoneyStoriesService.CardDto("commitment", 1, "HERO_STAT", "CALM_CONTEXT",
                monthLabel(month) + " · Planning", title, body, List.copyOf(components), List.of());
        var period = new MoneyStoriesService.PeriodDto("MONTH", month.atDay(1), month.atEndOfMonth(), monthLabel(month));
        var face = new MoneyStoriesService.CardFace("Monthly commitment", value, "calm");
        return new MoneyStoriesService.MoneyStoryApi("monthly-commitment", MoneyStoryType.MONTHLY_COMMITMENT.name(), 1,
                Instant.now(clock), period, face, List.of(card), null, MoneyStoryLevel.OBSERVATION,
                "monthly-commitment", 1, null, null);
    }

    private boolean hasEmiIn(UserLoanEntity loan, YearMonth month) {
        if (loan.getStatus() != LoanStatus.ACTIVE) return false;
        YearMonth first = YearMonth.from(loan.getFirstEmiDueDate());
        YearMonth last = first.plusMonths(loan.getTotalTenureMonths() - 1L);
        return !month.isBefore(first) && !month.isAfter(last);
    }

    private boolean hasSipIn(UserInvestmentEntity investment, YearMonth month) {
        return investment.getAssetType() == InvestmentAssetType.MUTUAL_FUND
                && investment.getSipStatus() == InvestmentSipStatus.ACTIVE
                && investment.getSipAmount() != null
                && investment.getSipStartMonth() != null
                && !month.isBefore(YearMonth.from(investment.getSipStartMonth()));
    }

    private MoneyStoriesService.Component component(String label, BigDecimal amount, String currency) {
        return new MoneyStoriesService.Component("MONEY", label, amount, currency, MoneyStoryRenderer.money(amount, currency));
    }

    private String monthLabel(YearMonth month) {
        return month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("en-IN"))
                + " " + month.getYear();
    }
}
