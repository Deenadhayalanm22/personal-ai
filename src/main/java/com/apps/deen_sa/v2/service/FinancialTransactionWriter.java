package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.v2.entity.FinancialTransactionEntity;
import com.apps.deen_sa.v2.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.v2.entity.UserReferenceEntity;
import com.apps.deen_sa.v2.repository.FinancialTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class FinancialTransactionWriter {
    private final FinancialTransactionRepository repository;
    private final ExpenseTaxonomyRegistry taxonomy;

    public FinancialTransactionEntity save(
            TransactionDraftExtractionEntity extraction,
            UserReferenceEntity merchant
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
        transaction.setSourceDraft(extraction.getDraft());
        transaction.setCreatedAt(Instant.now());
        transaction.setUpdatedAt(Instant.now());
        return repository.saveAndFlush(transaction);
    }
}
