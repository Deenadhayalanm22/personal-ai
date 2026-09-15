package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.FinancialTransactionEntity;
import com.apps.deen_sa.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.entity.UserReferenceEntity;
import com.apps.deen_sa.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

@Service
public class FinancialTransactionWriter {
    private final FinancialTransactionRepository repository;
    private final ExpenseTaxonomyRegistry taxonomy;
    private final MoneyStoryChangeService storyChanges;

    public FinancialTransactionWriter(FinancialTransactionRepository repository, ExpenseTaxonomyRegistry taxonomy) {
        this(repository, taxonomy, null);
    }
    @Autowired
    public FinancialTransactionWriter(FinancialTransactionRepository repository, ExpenseTaxonomyRegistry taxonomy,
                                      MoneyStoryChangeService storyChanges) {
        this.repository = repository; this.taxonomy = taxonomy; this.storyChanges = storyChanges;
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
        FinancialTransactionEntity saved = repository.saveAndFlush(transaction);
        if (storyChanges != null) storyChanges.changed(saved.getUser(), saved.getOccurredAt());
        return saved;
    }
}
