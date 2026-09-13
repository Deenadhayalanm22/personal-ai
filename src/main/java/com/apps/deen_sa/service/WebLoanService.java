package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.LoanStatus;
import com.apps.deen_sa.domain.LoanType;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserLoanEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.UserLoanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class WebLoanService {
    private final UserLoanRepository loans;

    public WebLoanService(UserLoanRepository loans) {
        this.loans = loans;
    }

    @Transactional
    public LoanResponse create(AppUserEntity user, LoanCreateRequest request) {
        validateCreate(request);
        UserLoanEntity loan = new UserLoanEntity();
        loan.setUser(user);
        apply(loan, request.loanName(), request.loanType(), request.lenderName(), request.originalPrincipal(),
                request.monthlyEmiAmount(), request.totalTenureMonths(), request.firstEmiDueDate(),
                request.status() == null ? LoanStatus.ACTIVE : request.status(), request.notes());
        return LoanResponse.from(loans.save(loan));
    }

    @Transactional(readOnly = true)
    public LoanListResponse list(AppUserEntity user) {
        return new LoanListResponse(loans.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(LoanResponse::from).toList());
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
        return LoanResponse.from(loans.save(loan));
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
                               LoanStatus status, String notes) {
        static LoanResponse from(UserLoanEntity loan) {
            return new LoanResponse(loan.getId(), loan.getLoanName(), loan.getLoanType(), loan.getLenderName(),
                    loan.getOriginalPrincipal(), loan.getMonthlyEmiAmount(), loan.getTotalTenureMonths(),
                    loan.getFirstEmiDueDate(), loan.getStatus(), loan.getNotes());
        }
    }

    public record LoanListResponse(List<LoanResponse> loans) { }
}
