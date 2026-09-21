package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.LoanStatus;
import com.apps.deen_sa.domain.LoanType;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserLoanEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.UserLoanRepository;
import com.apps.deen_sa.repository.LoanEmiOccurrenceRepository;
import com.apps.deen_sa.repository.UserActionItemRepository;
import com.apps.deen_sa.entity.LoanEmiOccurrenceEntity;
import com.apps.deen_sa.domain.LoanEmiOccurrenceStatus;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** FIN-EPIC-005 — Loans and mutual-fund planning. See docs/jira/personal-expense/FIN-EPIC-005-planning.md. */
@Service
public class WebLoanService {
    private final UserLoanRepository loans;
    private final MonthlyFinancialSnapshotService snapshots;
    private final Clock clock;
    private final LoanEmiOccurrenceRepository occurrences;
    private final UserActionItemRepository actions;

    public WebLoanService(UserLoanRepository loans) {
        this(loans, null, Clock.systemUTC(), null, null);
    }
    public WebLoanService(UserLoanRepository loans, MonthlyFinancialSnapshotService snapshots, Clock clock) {
        this(loans, snapshots, clock, null, null);
    }
    @Autowired
    public WebLoanService(UserLoanRepository loans, MonthlyFinancialSnapshotService snapshots, Clock clock, LoanEmiOccurrenceRepository occurrences, UserActionItemRepository actions) {
        this.loans = loans; this.snapshots = snapshots; this.clock = clock; this.occurrences = occurrences; this.actions = actions;
    }

    @Transactional
    public LoanResponse create(AppUserEntity user, LoanCreateRequest request) {
        validateCreate(request);
        UserLoanEntity loan = new UserLoanEntity();
        loan.setUser(user);
        apply(loan, request.loanName(), request.loanType(), request.lenderName(), request.originalPrincipal(),
                request.monthlyEmiAmount(), request.totalTenureMonths(), request.firstEmiDueDate(),
                request.status() == null ? LoanStatus.ACTIVE : request.status(), request.notes());
        UserLoanEntity saved = loans.save(loan); seedHistoricalOccurrences(saved); LoanResponse response = response(saved);
        if (snapshots != null) snapshots.refreshCurrent(user); // FIN-018: refresh commitment snapshot atomically with its loan source.
        return response;
    }

    @Transactional(readOnly = true)
    public LoanListResponse list(AppUserEntity user) {
        return new LoanListResponse(loans.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::response).toList());
    }

    @Transactional(readOnly = true)
    public LoanHistoryResponse history(AppUserEntity user, Long loanId) {
        UserLoanEntity loan = loans.findByIdAndUserId(loanId, user.getId())
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "Loan not found"));
        return new LoanHistoryResponse(loan.getId(), loan.getLoanName(), occurrenceResponses(loan));
    }

    @Transactional
    public LoanResponse update(AppUserEntity user, Long loanId, LoanUpdateRequest request) {
        if (request == null || request.hasNoChanges()) throw invalid("Provide at least one value to update");
        UserLoanEntity loan = loans.findByIdAndUserId(loanId, user.getId())
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "Loan not found"));
        apply(loan,
                request.loanName() == null ? loan.getLoanName() : request.loanName(),
                request.loanType() == null ? loan.getLoanType() : request.loanType(),
                request.lenderName() == null ? loan.getLenderName() : request.lenderName(),
                request.originalPrincipal() == null ? loan.getOriginalPrincipal() : request.originalPrincipal(),
                request.monthlyEmiAmount() == null ? loan.getMonthlyEmiAmount() : request.monthlyEmiAmount(),
                request.totalTenureMonths() == null ? loan.getTotalTenureMonths() : request.totalTenureMonths(),
                request.firstEmiDueDate() == null ? loan.getFirstEmiDueDate() : request.firstEmiDueDate(),
                loan.getStatus(),
                request.notes() == null ? loan.getNotes() : request.notes());
        loan.setUpdatedAt(Instant.now());
        LoanResponse response = response(loans.save(loan));
        if (snapshots != null) snapshots.refreshCurrent(user); // FIN-018: refresh commitment snapshot atomically with its loan source.
        return response;
    }

    @Transactional
    public void delete(AppUserEntity user, Long loanId) {
        UserLoanEntity loan = loans.findByIdAndUserId(loanId, user.getId())
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "Loan not found"));
        if (occurrences != null) occurrences.deleteByLoanId(loan.getId());
        if (actions != null) actions.deleteByUserIdAndReferenceTypeAndReferenceId(user.getId(), "LOAN", loan.getId());
        loans.delete(loan);
        if (snapshots != null) snapshots.refreshCurrent(user);
    }

    @Transactional
    public LoanResponse markPaid(AppUserEntity user, Long loanId, java.time.YearMonth month) {
        UserLoanEntity loan = loans.findByIdAndUserId(loanId, user.getId()).orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "Loan not found"));
        LocalDate dueMonth = month.atDay(1);
        if (!isScheduledMonth(loan, dueMonth)) throw invalid("EMI month is outside this loan's tenure");
        LoanEmiOccurrenceEntity occurrence = occurrence(loan, dueMonth);
        if (occurrence.getStatus() == LoanEmiOccurrenceStatus.PAID) throw new WebApiException(HttpStatus.CONFLICT, "EMI_ALREADY_PAID", "This EMI has already been paid");
        occurrence.setStatus(LoanEmiOccurrenceStatus.PAID); occurrence.setPaidAmount(loan.getMonthlyEmiAmount());
        occurrence.setPaidAt(LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())))); occurrence.setUpdatedAt(Instant.now(clock)); occurrences.save(occurrence);
        if (dueMonth.equals(loan.getFirstEmiDueDate().plusMonths(loan.getTotalTenureMonths() - 1L).withDayOfMonth(1))) {
            loan.setStatus(LoanStatus.CLOSED);
            loan.setUpdatedAt(Instant.now(clock));
            loans.save(loan);
            if (actions != null) actions.deleteByUserIdAndReferenceTypeAndReferenceId(user.getId(), "LOAN", loan.getId());
        }
        if (snapshots != null) snapshots.refreshCurrent(user);
        return response(loan);
    }

    private boolean isScheduledMonth(UserLoanEntity loan, LocalDate month) {
        LocalDate first = loan.getFirstEmiDueDate().withDayOfMonth(1);
        LocalDate last = first.plusMonths(loan.getTotalTenureMonths() - 1L);
        return !month.isBefore(first) && !month.isAfter(last);
    }

    private LoanResponse response(UserLoanEntity loan) { return LoanResponse.from(loan, clock, visibleOccurrences(occurrenceResponses(loan))); }
    private List<EmiOccurrenceResponse> visibleOccurrences(List<EmiOccurrenceResponse> values) {
        int next = java.util.stream.IntStream.range(0, values.size()).filter(index -> values.get(index).status() != LoanEmiOccurrenceStatus.PAID).findFirst().orElse(-1);
        return next < 0 ? values.stream().skip(Math.max(0, values.size() - 1L)).toList() : values.subList(Math.max(0, next - 1), Math.min(values.size(), next + 1));
    }
    private List<EmiOccurrenceResponse> occurrenceResponses(UserLoanEntity loan) {
        if (occurrences == null || loan.getId() == null) return List.of();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(loan.getUser().getTimezone())));
        java.util.Map<LocalDate, LoanEmiOccurrenceEntity> saved = occurrences.findByLoanIdOrderByDueMonthAsc(loan.getId()).stream().collect(java.util.stream.Collectors.toMap(LoanEmiOccurrenceEntity::getDueMonth, value -> value));
        return java.util.stream.IntStream.range(0, loan.getTotalTenureMonths()).mapToObj(index -> {
            LocalDate due = loan.getFirstEmiDueDate().plusMonths(index); LocalDate month = due.withDayOfMonth(1); LoanEmiOccurrenceEntity entity = saved.get(month);
            LoanEmiOccurrenceStatus status = entity == null ? (month.isBefore(today.withDayOfMonth(1)) ? LoanEmiOccurrenceStatus.PAID : month.equals(today.withDayOfMonth(1)) ? LoanEmiOccurrenceStatus.DUE : LoanEmiOccurrenceStatus.UPCOMING) : entity.getStatus();
            return new EmiOccurrenceResponse(month.toString().substring(0, 7), due, status, loan.getMonthlyEmiAmount(), entity == null ? (status == LoanEmiOccurrenceStatus.PAID ? loan.getMonthlyEmiAmount() : null) : entity.getPaidAmount(), entity == null ? (status == LoanEmiOccurrenceStatus.PAID ? due : null) : entity.getPaidAt());
        }).toList();
    }
    private void seedHistoricalOccurrences(UserLoanEntity loan) {
        if (occurrences == null) return;
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(loan.getUser().getTimezone())));
        for (int index = 0; index < loan.getTotalTenureMonths(); index++) { LocalDate due = loan.getFirstEmiDueDate().plusMonths(index); if (due.isAfter(today)) break; LoanEmiOccurrenceEntity value = new LoanEmiOccurrenceEntity(); value.setLoan(loan); value.setDueMonth(due.withDayOfMonth(1)); value.setDueDate(due); value.setStatus(LoanEmiOccurrenceStatus.PAID); value.setPaidAmount(loan.getMonthlyEmiAmount()); value.setPaidAt(due); occurrences.save(value); }
    }
    private LoanEmiOccurrenceEntity occurrence(UserLoanEntity loan, LocalDate month) {
        if (occurrences == null) throw new IllegalStateException("Loan EMI occurrences are unavailable");
        return occurrences.findByLoanIdAndDueMonth(loan.getId(), month).orElseGet(() -> { LoanEmiOccurrenceEntity value = new LoanEmiOccurrenceEntity(); value.setLoan(loan); value.setDueMonth(month); value.setDueDate(loan.getFirstEmiDueDate().plusMonths(java.time.temporal.ChronoUnit.MONTHS.between(loan.getFirstEmiDueDate().withDayOfMonth(1), month))); value.setStatus(LoanEmiOccurrenceStatus.DUE); return value; });
    }

    private void validateCreate(LoanCreateRequest request) {
        if (request == null) throw invalid("Loan details are required");
        apply(new UserLoanEntity(), request.loanName(), request.loanType(), request.lenderName(), request.originalPrincipal(),
                request.monthlyEmiAmount(), request.totalTenureMonths(), request.firstEmiDueDate(),
                request.status() == null ? LoanStatus.ACTIVE : request.status(), request.notes());
    }

    private void apply(UserLoanEntity loan, String loanName, LoanType loanType, String lenderName,
                       BigDecimal originalPrincipal, BigDecimal monthlyEmiAmount, Integer totalTenureMonths,
                       LocalDate firstEmiDueDate, LoanStatus status, String notes) {
        loan.setLoanName(requiredText(loanName, "loanName"));
        if (loanType == null) throw invalid("loanType is required");
        loan.setLoanType(loanType);
        loan.setLenderName(requiredText(lenderName, "lenderName"));
        loan.setOriginalPrincipal(positiveAmount(originalPrincipal, "originalPrincipal"));
        loan.setMonthlyEmiAmount(positiveAmount(monthlyEmiAmount, "monthlyEmiAmount"));
        if (totalTenureMonths == null || totalTenureMonths <= 0) throw invalid("totalTenureMonths must be greater than zero");
        loan.setTotalTenureMonths(totalTenureMonths);
        if (firstEmiDueDate == null) throw invalid("firstEmiDueDate is required");
        loan.setFirstEmiDueDate(firstEmiDueDate);
        if (status == null) throw invalid("status is required");
        loan.setStatus(status);
        loan.setNotes(optionalText(notes));
    }

    private String requiredText(String value, String field) {
        String normalized = optionalText(value);
        if (normalized == null) throw invalid(field + " is required");
        if (normalized.length() > 255) throw invalid(field + " must not exceed 255 characters");
        return normalized;
    }

    private String optionalText(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private BigDecimal positiveAmount(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) throw invalid(field + " must be greater than zero");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private WebApiException invalid(String message) {
        return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN", message);
    }

    public record LoanCreateRequest(String loanName, LoanType loanType, String lenderName,
                                    BigDecimal originalPrincipal, BigDecimal monthlyEmiAmount,
                                    Integer totalTenureMonths, LocalDate firstEmiDueDate,
                                    LoanStatus status, String notes) { }

    public record LoanUpdateRequest(String loanName, LoanType loanType, String lenderName,
                                    BigDecimal originalPrincipal, BigDecimal monthlyEmiAmount,
                                    Integer totalTenureMonths, LocalDate firstEmiDueDate,
                                    String notes) {
        boolean hasNoChanges() {
            return loanName == null && loanType == null && lenderName == null && originalPrincipal == null
                    && monthlyEmiAmount == null && totalTenureMonths == null && firstEmiDueDate == null
                    && notes == null;
        }
    }

    public record LoanResponse(Long id, String loanName, LoanType loanType, String lenderName,
                               BigDecimal originalPrincipal, BigDecimal monthlyEmiAmount,
                               Integer totalTenureMonths, LocalDate firstEmiDueDate,
                               LoanStatus status, String notes, int completedEmiCount, int remainingEmiCount, List<EmiOccurrenceResponse> emiOccurrences) {
        static LoanResponse from(UserLoanEntity loan, Clock clock, List<EmiOccurrenceResponse> occurrences) {
            LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(loan.getUser().getTimezone())));
            int completed = loan.getStatus() == LoanStatus.CLOSED ? loan.getTotalTenureMonths()
                    : Math.max(0, Math.min(loan.getTotalTenureMonths(),
                    (int) java.time.temporal.ChronoUnit.MONTHS.between(loan.getFirstEmiDueDate().withDayOfMonth(1), today.withDayOfMonth(1))
                            + (today.getDayOfMonth() >= loan.getFirstEmiDueDate().getDayOfMonth() ? 1 : 0)));
            return new LoanResponse(loan.getId(), loan.getLoanName(), loan.getLoanType(), loan.getLenderName(),
                    loan.getOriginalPrincipal(), loan.getMonthlyEmiAmount(), loan.getTotalTenureMonths(),
                    loan.getFirstEmiDueDate(), loan.getStatus(), loan.getNotes(), completed, loan.getTotalTenureMonths() - completed, occurrences);
        }
    }
    public record EmiOccurrenceResponse(String month, LocalDate dueDate, LoanEmiOccurrenceStatus status, BigDecimal plannedAmount, BigDecimal paidAmount, LocalDate paidAt) { }

    public record LoanHistoryResponse(Long id, String loanName, List<EmiOccurrenceResponse> history) { }

    public record LoanListResponse(List<LoanResponse> loans) { }
}
