package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentMatchStatus;
import com.apps.deen_sa.domain.RecurringCommitmentStatus;
import com.apps.deen_sa.entity.FinancialTransactionEntity;
import com.apps.deen_sa.entity.UserRecurringCommitmentEntity;
import com.apps.deen_sa.repository.UserRecurringCommitmentRepository;
import org.springframework.stereotype.Service;
import java.time.YearMonth;
import java.util.List;

/** FIN-021 — capture stays uninterrupted; uncertain matches become portal-review candidates. */
@Service
public class RecurringCommitmentMatcher {
    private final UserRecurringCommitmentRepository commitments;
    public RecurringCommitmentMatcher(UserRecurringCommitmentRepository commitments) { this.commitments = commitments; }
    public void classify(FinancialTransactionEntity transaction) {
        List<UserRecurringCommitmentEntity> categoryMatches = commitments.findAllOwned(transaction.getUser().getId()).stream()
                .filter(c -> c.getStatus() == RecurringCommitmentStatus.ACTIVE && !YearMonth.from(transaction.getOccurredAt()).atDay(1).isBefore(c.getEffectiveMonth()))
                .filter(c -> java.util.Objects.equals(c.getCategory(), transaction.getCategory()) && java.util.Objects.equals(c.getSubcategory(), transaction.getSubcategory())).toList();
        List<UserRecurringCommitmentEntity> exact = categoryMatches.stream().filter(c -> c.getMerchant() != null && transaction.getMerchant() != null && c.getMerchant().getId().equals(transaction.getMerchant().getId())).toList();
        if (exact.size() == 1) { transaction.setRecurringCommitment(exact.getFirst()); transaction.setCommitmentMatchStatus(CommitmentMatchStatus.MATCHED); }
        else if (!categoryMatches.isEmpty()) transaction.setCommitmentMatchStatus(CommitmentMatchStatus.CANDIDATE);
        else transaction.setCommitmentMatchStatus(CommitmentMatchStatus.NOT_LINKED);
    }
}
