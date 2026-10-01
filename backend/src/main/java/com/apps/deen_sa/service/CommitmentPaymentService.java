package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentMatchStatus;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;

/** FIN-EPIC-002/006: one authoritative expense for a confirmed recurring payment. */
@Service
@RequiredArgsConstructor
public class CommitmentPaymentService {
    private final FinancialTransactionRepository transactions;
    private final RecurringCommitmentOccurrenceRepository occurrences;
    private final RecurringCommitmentExtraRepository extras;
    private final ExpenseDailyAggregateRepository aggregates;
    private final ExpenseTaxonomyRegistry taxonomy;
    private final Clock clock;

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public FinancialTransactionEntity record(UserRecurringCommitmentEntity commitment, BigDecimal amount,
            LocalDate date, Long existingTransactionId, String reference, String description) {
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        if (rounded.signum() <= 0) throw invalid("Payment must be at least 0.01");
        FinancialTransactionEntity payment;
        if (existingTransactionId != null) {
            payment = transactions.findOwnedForUpdate(existingTransactionId, commitment.getUser().getId())
                    .orElseThrow(() -> invalid("Expense is unavailable"));
            if (payment.getAmount().compareTo(rounded) != 0 || !payment.getOccurredAt().equals(date))
                throw invalid("The selected expense must have the same payment amount and date");
            if (payment.getPaymentReference() != null || occurrences.findByPaymentTransactionId(payment.getId()).isPresent()
                    || extras.findByPaymentTransactionId(payment.getId()).isPresent()
                    || (payment.getRecurringCommitment() != null && !payment.getRecurringCommitment().getId().equals(commitment.getId())))
                throw invalid("Expense is already linked to another payment or commitment");
        } else {
            payment = new FinancialTransactionEntity();
            payment.setUser(commitment.getUser()); payment.setAmount(rounded); payment.setOccurredAt(date);
            payment.setOrigin("COMMITMENT_PAYMENT"); payment.setPaymentReference(reference);
            payment.setDescription(description.length() > 255 ? description.substring(0, 255) : description);
            payment.setCategory(commitment.getCategory()); payment.setSubcategory(commitment.getSubcategory());
            payment.setMerchant(commitment.getMerchant());
            taxonomy.spendingNatureFor(commitment.getCategory(), commitment.getSubcategory()).ifPresent(payment::setSpendingNature);
            payment.setCreatedAt(java.time.Instant.now(clock));
        }
        payment.setPaymentReference(reference);
        payment.setRecurringCommitment(commitment); payment.setCommitmentMatchStatus(CommitmentMatchStatus.MATCHED);
        payment.setUpdatedAt(java.time.Instant.now(clock));
        transactions.saveAndFlush(payment); aggregates.markDateForRebuild(date);
        return payment;
    }
    private WebApiException invalid(String reason) {
        return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_COMMITMENT_PAYMENT", reason);
    }
}
