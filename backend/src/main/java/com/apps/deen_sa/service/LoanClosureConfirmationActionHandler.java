package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.LoanStatus;
import com.apps.deen_sa.domain.UserActionItemType;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserActionItemEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.UserLoanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** FIN-EPIC-005 — Loans and mutual-fund planning. See docs/jira/personal-expense/FIN-EPIC-005-planning.md. */
@Component
public class LoanClosureConfirmationActionHandler implements UserActionCompletionHandler {
    private final UserLoanRepository loans;

    public LoanClosureConfirmationActionHandler(UserLoanRepository loans) { this.loans = loans; }

    @Override public UserActionItemType actionType() { return UserActionItemType.LOAN_CLOSURE_CONFIRMATION; }

    @Override
    public void complete(AppUserEntity user, UserActionItemEntity action) {
        var loan = loans.findByIdAndUserId(action.getReferenceId(), user.getId())
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "Loan not found"));
        loan.setStatus(LoanStatus.CLOSED);
        loans.save(loan);
    }
}
