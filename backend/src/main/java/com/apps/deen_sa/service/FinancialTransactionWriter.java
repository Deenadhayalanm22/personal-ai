package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.FinancialTransactionEntity;
import com.apps.deen_sa.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.entity.UserReferenceEntity;
import com.apps.deen_sa.repository.FinancialTransactionRepository;
import com.apps.deen_sa.repository.ExpenseDailyAggregateRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

@Service
public class FinancialTransactionWriter {
    private final FinancialTransactionRepository repository;
    private final ExpenseTaxonomyRegistry taxonomy;
    private final ExpenseDailyAggregateRepository aggregates;
    private final RecurringCommitmentMatcher commitmentMatcher;

    public FinancialTransactionWriter(FinancialTransactionRepository repository, ExpenseTaxonomyRegistry taxonomy) {
        this(repository, taxonomy, null, null);
    }
    public FinancialTransactionWriter(FinancialTransactionRepository repository, ExpenseTaxonomyRegistry taxonomy,
                                      ExpenseDailyAggregateRepository aggregates) { this(repository, taxonomy, aggregates, null); }
    @Autowired
    public FinancialTransactionWriter(FinancialTransactionRepository repository, ExpenseTaxonomyRegistry taxonomy,
                                      ExpenseDailyAggregateRepository aggregates, RecurringCommitmentMatcher commitmentMatcher) {
        this.repository = repository; this.taxonomy = taxonomy; this.aggregates = aggregates; this.commitmentMatcher = commitmentMatcher;
    }

    public FinancialTransactionEntity save(
            TransactionDraftExtractionEntity extraction,
            UserReferenceEntity merchant,
            UserReferenceEntity sourceAccount
    ) {
        if (extraction.getAmount() == null) {
            throw new IllegalStateException("Cannot confirm an expense without an amount");
        }

        FinancialTransactionEntity transaction = new FinancialTransactionEntity();
        transaction.setUser(extraction.getDraft().getUser());
        transaction.setAmount(extraction.getAmount());
        transaction.setOccurredAt(extraction.getOccurredAt());
        transaction.setCategory(extraction.getCategoryId());
        transaction.setSubcategory(extraction.getSubcategoryId());
        transaction.setSpendingNature(taxonomy.spendingNatureFor(
                        extraction.getCategoryId(), extraction.getSubcategoryId())
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot confirm an expense without a taxonomy spending nature")));
        transaction.setMerchant(merchant);
        transaction.setSourceAccount(sourceAccount);
        transaction.setSourceDraft(extraction.getDraft());
        transaction.setCreatedAt(Instant.now());
        transaction.setUpdatedAt(Instant.now());
        if (commitmentMatcher != null) commitmentMatcher.classify(transaction);
        FinancialTransactionEntity saved = repository.saveAndFlush(transaction);
        if (aggregates != null) aggregates.markDateForRebuild(saved.getOccurredAt());
        return saved;
    }
}
