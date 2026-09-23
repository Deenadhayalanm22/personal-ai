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
import java.time.YearMonth;
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
        if (hasRecordedOutcome(loan) && (request.originalPrincipal() != null || request.monthlyEmiAmount() != null || request.totalTenureMonths() != null || request.firstEmiDueDate() != null)) throw invalid("Payment history cannot be changed; restructure remaining loan instead");
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
        if (loan.getStatus() != LoanStatus.ACTIVE || !isScheduledMonth(loan, dueMonth)) throw invalid("EMI month is outside this active loan's tenure");
        LoanEmiOccurrenceEntity occurrence = occurrence(loan, dueMonth);
        if (occurrence.getDueDate().isAfter(LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone()))))) throw invalid("EMI is not due yet");
        if (occurrence.getStatus() == LoanEmiOccurrenceStatus.SKIPPED) throw new WebApiException(HttpStatus.CONFLICT, "EMI_ALREADY_RECORDED", "This EMI was skipped");
        if (occurrence.getStatus() == LoanEmiOccurrenceStatus.PAID) throw new WebApiException(HttpStatus.CONFLICT, "EMI_ALREADY_PAID", "This EMI has already been paid");
        occurrence.setStatus(LoanEmiOccurrenceStatus.PAID); occurrence.setPlannedAmount(loan.getMonthlyEmiAmount()); occurrence.setPaidAmount(loan.getMonthlyEmiAmount());
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

    @Transactional
    public LoanResponse skip(AppUserEntity user, Long loanId, java.time.YearMonth month, SkipRequest request) {
        UserLoanEntity loan = owned(user, loanId); LocalDate dueMonth = month.atDay(1);
        if (loan.getStatus() != LoanStatus.ACTIVE || !isScheduledMonth(loan, dueMonth)) throw invalid("EMI month is outside this active loan's tenure");
        LoanEmiOccurrenceEntity occurrence = occurrence(loan, dueMonth);
        if (occurrence.getDueDate().isAfter(LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone()))))) throw invalid("EMI is not due yet");
        if (occurrence.getStatus() == LoanEmiOccurrenceStatus.PAID || occurrence.getStatus() == LoanEmiOccurrenceStatus.SKIPPED) throw new WebApiException(HttpStatus.CONFLICT, "EMI_ALREADY_RECORDED", "This EMI already has an outcome");
        if (request != null && request.bankPenaltyAmount() != null) occurrence.setBankPenaltyAmount(positiveAmount(request.bankPenaltyAmount(), "bankPenaltyAmount"));
        occurrence.setPlannedAmount(loan.getMonthlyEmiAmount()); occurrence.setStatus(LoanEmiOccurrenceStatus.SKIPPED); occurrence.setUpdatedAt(Instant.now(clock)); occurrences.save(occurrence);
        loan.setTotalTenureMonths(loan.getTotalTenureMonths() + 1); loan.setUpdatedAt(Instant.now(clock)); loans.save(loan);
        if (snapshots != null) snapshots.refreshCurrent(user); return response(loan);
    }

    @Transactional
    public LoanResponse preClose(AppUserEntity user, Long loanId, PreCloseRequest request) {
        if (request == null || request.settlementAmount() == null || request.settlementAmount().signum() <= 0) throw invalid("settlementAmount must be greater than zero");
        UserLoanEntity loan = owned(user, loanId); LocalDate month = LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone()))).withDayOfMonth(1);
        if (loan.getStatus() != LoanStatus.ACTIVE || !isScheduledMonth(loan, month)) throw invalid("Loan has no payable EMI this month");
        LoanEmiOccurrenceEntity occurrence = occurrence(loan, month); occurrence.setStatus(LoanEmiOccurrenceStatus.PAID); occurrence.setPlannedAmount(loan.getMonthlyEmiAmount()); occurrence.setPaidAmount(request.settlementAmount().setScale(2, RoundingMode.HALF_UP)); occurrence.setPaidAt(LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())))); occurrence.setPreClosureSettlement(true); occurrences.save(occurrence);
        occurrences.deleteByLoanIdAndDueMonthGreaterThanEqual(loan.getId(), month.plusMonths(1));
        loan.setTotalTenureMonths((int) java.time.temporal.ChronoUnit.MONTHS.between(loan.getFirstEmiDueDate().withDayOfMonth(1), month) + 1); loan.setStatus(LoanStatus.CLOSED); loans.save(loan);
        if (actions != null) actions.deleteByUserIdAndReferenceTypeAndReferenceId(user.getId(), "LOAN", loan.getId()); if (snapshots != null) snapshots.refreshCurrent(user); return response(loan);
    }

    @Transactional
    public LoanResponse restructure(AppUserEntity user, Long loanId, RestructureRequest request) {
        if (request == null || request.effectiveMonth() == null || request.monthlyEmiAmount() == null || request.monthlyEmiAmount().signum() <= 0 || request.remainingTenureMonths() == null || request.remainingTenureMonths() <= 0) throw invalid("Valid restructure details are required");
        UserLoanEntity loan = owned(user, loanId); LocalDate effective = request.effectiveMonth().atDay(1);
        if (loan.getStatus() != LoanStatus.ACTIVE || !hasRecordedOutcome(loan) || !isScheduledMonth(loan, effective) || effective.isBefore(LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone()))).withDayOfMonth(1))) throw invalid("Restructure must start in a future unpaid loan month");
        if (occurrences.findByLoanIdOrderByDueMonthAsc(loan.getId()).stream().anyMatch(value -> !value.getDueMonth().isBefore(effective) && (value.getStatus() == LoanEmiOccurrenceStatus.PAID || value.getStatus() == LoanEmiOccurrenceStatus.SKIPPED))) throw invalid("Restructure cannot change recorded outcomes");
        occurrences.deleteByLoanIdAndDueMonthGreaterThanEqual(loan.getId(), effective); loan.setRestructuredFrom(effective); loan.setMonthlyEmiAmount(request.monthlyEmiAmount().setScale(2, RoundingMode.HALF_UP));
        loan.setTotalTenureMonths((int) java.time.temporal.ChronoUnit.MONTHS.between(loan.getFirstEmiDueDate().withDayOfMonth(1), effective) + request.remainingTenureMonths()); loans.save(loan);
        if (snapshots != null) snapshots.refreshCurrent(user); return response(loan);
    }

    private UserLoanEntity owned(AppUserEntity user, Long id) { return loans.findByIdAndUserId(id, user.getId()).orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "Loan not found")); }
    private boolean hasRecordedOutcome(UserLoanEntity loan) { return occurrences != null && occurrences.findByLoanIdOrderByDueMonthAsc(loan.getId()).stream().anyMatch(value -> value.getStatus() == LoanEmiOccurrenceStatus.PAID || value.getStatus() == LoanEmiOccurrenceStatus.SKIPPED); }

    private boolean isScheduledMonth(UserLoanEntity loan, LocalDate month) {
        LocalDate first = loan.getFirstEmiDueDate().withDayOfMonth(1);
        LocalDate last = first.plusMonths(loan.getTotalTenureMonths() - 1L);
        return !month.isBefore(first) && !month.isAfter(last);
    }

    private LoanResponse response(UserLoanEntity loan) {
        List<EmiOccurrenceResponse> allOccurrences = occurrenceResponses(loan);
        return LoanResponse.from(loan, visibleOccurrences(allOccurrences), allOccurrences, hasRecordedOutcome(loan));
    }
    private List<EmiOccurrenceResponse> visibleOccurrences(List<EmiOccurrenceResponse> values) {
        int next = java.util.stream.IntStream.range(0, values.size()).filter(index -> values.get(index).status() != LoanEmiOccurrenceStatus.PAID && values.get(index).status() != LoanEmiOccurrenceStatus.SKIPPED).findFirst().orElse(-1);
        return next < 0 ? values.stream().skip(Math.max(0, values.size() - 1L)).toList() : values.subList(Math.max(0, next - 1), Math.min(values.size(), next + 1));
    }
    private List<EmiOccurrenceResponse> occurrenceResponses(UserLoanEntity loan) {
        if (occurrences == null || loan.getId() == null) return List.of();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(loan.getUser().getTimezone())));
        java.util.Map<LocalDate, LoanEmiOccurrenceEntity> saved = occurrences.findByLoanIdOrderByDueMonthAsc(loan.getId()).stream().collect(java.util.stream.Collectors.toMap(LoanEmiOccurrenceEntity::getDueMonth, value -> value));
        return java.util.stream.IntStream.range(0, loan.getTotalTenureMonths()).mapToObj(index -> {
            LocalDate due = loan.getFirstEmiDueDate().plusMonths(index); LocalDate month = due.withDayOfMonth(1); LoanEmiOccurrenceEntity entity = saved.get(month);
            LoanEmiOccurrenceStatus status = entity == null ? (!due.isAfter(today) ? LoanEmiOccurrenceStatus.DUE : LoanEmiOccurrenceStatus.UPCOMING) : entity.getStatus();
            return new EmiOccurrenceResponse(month.toString().substring(0, 7), due, status, entity != null && entity.getPlannedAmount() != null ? entity.getPlannedAmount() : loan.getMonthlyEmiAmount(), entity == null ? null : entity.getPaidAmount(), entity == null ? null : entity.getPaidAt(), entity == null ? null : entity.getBankPenaltyAmount(), entity != null && entity.isPreClosureSettlement());
        }).toList();
    }
    private void seedHistoricalOccurrences(UserLoanEntity loan) {
        if (occurrences == null) return;
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(loan.getUser().getTimezone())));
        for (int index = 0; index < loan.getTotalTenureMonths(); index++) { LocalDate due = loan.getFirstEmiDueDate().plusMonths(index); if (!due.isBefore(today)) break; LoanEmiOccurrenceEntity value = new LoanEmiOccurrenceEntity(); value.setLoan(loan); value.setDueMonth(due.withDayOfMonth(1)); value.setDueDate(due); value.setStatus(LoanEmiOccurrenceStatus.PAID); value.setPlannedAmount(loan.getMonthlyEmiAmount()); value.setPaidAmount(loan.getMonthlyEmiAmount()); value.setPaidAt(due); occurrences.save(value); }
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
                               LoanStatus status, String notes, int completedEmiCount, int remainingEmiCount, List<EmiOccurrenceResponse> emiOccurrences, boolean hasRecordedOutcome, LocalDate restructuredFrom) {
        static LoanResponse from(UserLoanEntity loan, List<EmiOccurrenceResponse> occurrences,
                                 List<EmiOccurrenceResponse> allOccurrences, boolean hasRecordedOutcome) {
            int completed = (int) allOccurrences.stream().filter(value -> value.status() == LoanEmiOccurrenceStatus.PAID).count();
            return new LoanResponse(loan.getId(), loan.getLoanName(), loan.getLoanType(), loan.getLenderName(),
                    loan.getOriginalPrincipal(), loan.getMonthlyEmiAmount(), loan.getTotalTenureMonths(),
                    loan.getFirstEmiDueDate(), loan.getStatus(), loan.getNotes(), completed, loan.getTotalTenureMonths() - completed, occurrences, hasRecordedOutcome, loan.getRestructuredFrom());
        }
    }
    public record EmiOccurrenceResponse(String month, LocalDate dueDate, LoanEmiOccurrenceStatus status, BigDecimal plannedAmount, BigDecimal paidAmount, LocalDate paidAt, BigDecimal bankPenaltyAmount, boolean preClosureSettlement) { }

    public record SkipRequest(BigDecimal bankPenaltyAmount) { }
    public record PreCloseRequest(BigDecimal settlementAmount) { }
    public record RestructureRequest(YearMonth effectiveMonth, BigDecimal monthlyEmiAmount, Integer remainingTenureMonths) { }

    public record LoanHistoryResponse(Long id, String loanName, List<EmiOccurrenceResponse> history) { }

    public record LoanListResponse(List<LoanResponse> loans) { }
}
