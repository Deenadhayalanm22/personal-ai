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
import java.util.Objects;

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
        List<UserLoanEntity> activeLoans = loans.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(loan -> hasEmiIn(loan, month)).toList();
        BigDecimal emiTotal = activeLoans.stream().map(UserLoanEntity::getMonthlyEmiAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<UserInvestmentEntity> activeSips = investments.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(investment -> hasSipIn(investment, month)).toList();
        BigDecimal sipTotal = activeSips.stream().map(UserInvestmentEntity::getSipAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = emiTotal.add(sipTotal);
        String currency = user.getCurrency();
        String value = MoneyStoryRenderer.money(total, currency);
        List<MoneyStoriesService.Component> components = new ArrayList<>();
        if (emiTotal.signum() > 0) components.add(component("Debt repayments", emiTotal, currency));
        if (sipTotal.signum() > 0) components.add(component("Planned investing", sipTotal, currency));

        String title = total.signum() > 0 ? "Your known monthly commitment" : "Your monthly commitment starts here";
        String body = total.signum() > 0
                ? commitmentBody(value, emiTotal, sipTotal, currency)
                : "Add a loan or a mutual fund SIP and it will appear here as part of your monthly commitment.";
        List<MoneyStoriesService.EvidenceTransaction> evidenceRows = new ArrayList<>();
        activeLoans.forEach(loan -> evidenceRows.add(loanEvidence(loan, month, currency)));
        activeSips.forEach(investment -> evidenceRows.add(sipEvidence(investment, currency)));
        List<MoneyStoriesService.Action> actions = evidenceRows.isEmpty() ? List.of()
                : List.of(new MoneyStoriesService.Action("OPEN_EVIDENCE", "View included commitments"));
        var card = new MoneyStoriesService.CardDto("commitment", 1, "HERO_STAT", "CALM_CONTEXT",
                monthLabel(month) + " · Planning", title, body, List.copyOf(components), actions);
        var period = new MoneyStoriesService.PeriodDto("MONTH", month.atDay(1), month.atEndOfMonth(), monthLabel(month));
        var face = new MoneyStoriesService.CardFace("Monthly commitment", value, "calm");
        var evidence = new MoneyStoriesService.EvidenceDto("Commitment sources by bucket", evidenceRows.size(),
                component("Monthly total", total, currency), List.copyOf(evidenceRows));
        return new MoneyStoriesService.MoneyStoryApi("monthly-commitment", MoneyStoryType.MONTHLY_COMMITMENT.name(), 1,
                Instant.now(clock), period, face, List.of(card), evidence, MoneyStoryLevel.OBSERVATION,
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

    private String commitmentBody(String total, BigDecimal debt, BigDecimal investing, String currency) {
        if (debt.signum() > 0 && investing.signum() > 0) {
            return "Your full intended commitment is " + total + ": "
                    + MoneyStoryRenderer.money(debt, currency) + " in debt repayments and "
                    + MoneyStoryRenderer.money(investing, currency) + " in planned investing.";
        }
        if (debt.signum() > 0) return "Your required debt repayments total " + total + " this month.";
        return "Your planned investing total is " + total + " this month.";
    }

    private MoneyStoriesService.EvidenceTransaction loanEvidence(UserLoanEntity loan, YearMonth month, String currency) {
        LocalDate due = LocalDate.of(month.getYear(), month.getMonth(), Math.min(loan.getFirstEmiDueDate().getDayOfMonth(), month.lengthOfMonth()));
        return new MoneyStoriesService.EvidenceTransaction("loan:" + sourceId(loan.getId(), loan.getLoanName()),
                due.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")), loan.getLoanName(), "Loan EMI",
                component("Monthly EMI", loan.getMonthlyEmiAmount(), currency), loan.getLenderName());
    }

    private MoneyStoriesService.EvidenceTransaction sipEvidence(UserInvestmentEntity investment, String currency) {
        String due = "Every month";
        if (investment.getSipDay() != null) due = investment.getSipDay() + ordinal(investment.getSipDay()) + " of month";
        return new MoneyStoriesService.EvidenceTransaction("sip:" + sourceId(investment.getId(), investment.getDisplayNameSnapshot()),
                due, investment.getDisplayNameSnapshot(), "Mutual fund SIP",
                component("Monthly SIP", investment.getSipAmount(), currency), "Active SIP");
    }

    private String sourceId(Long id, String fallback) { return id == null ? Objects.requireNonNullElse(fallback, "unknown") : id.toString(); }
    private String ordinal(int day) { return day % 100 >= 11 && day % 100 <= 13 ? "th" : switch (day % 10) {
        case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th";
    }; }

    private String monthLabel(YearMonth month) {
        return month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("en-IN"))
                + " " + month.getYear();
    }
}
