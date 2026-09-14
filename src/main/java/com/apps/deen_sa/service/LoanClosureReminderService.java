package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.LoanStatus;
import com.apps.deen_sa.domain.UserActionItemType;
import com.apps.deen_sa.entity.UserLoanEntity;
import com.apps.deen_sa.repository.UserLoanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class LoanClosureReminderService {
    private static final String REFERENCE_TYPE = "LOAN";
    private final UserLoanRepository loans;
    private final ActionManagementService actions;
    private final Clock clock;

    public LoanClosureReminderService(UserLoanRepository loans, ActionManagementService actions, Clock clock) {
        this.loans = loans;
        this.actions = actions;
        this.clock = clock;
    }

    @Transactional
    public void createDueReminders() {
        for (UserLoanEntity loan : loans.findByStatus(LoanStatus.ACTIVE)) {
            LocalDate finalEmiDate = loan.getFirstEmiDueDate().plusMonths(loan.getTotalTenureMonths() - 1L);
            LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(loan.getUser().getTimezone())));
            if (!finalEmiDate.isAfter(today)) {
                actions.enqueue(new ActionManagementService.ActionRequest(
                        loan.getUser(), UserActionItemType.LOAN_CLOSURE_CONFIRMATION, REFERENCE_TYPE, loan.getId(),
                        "Confirm loan closure",
                        "%s was scheduled to finish on %s. Mark it closed if the final EMI was paid."
                                .formatted(loan.getLoanName(), finalEmiDate),
                        finalEmiDate));
            }
        }
    }
}
