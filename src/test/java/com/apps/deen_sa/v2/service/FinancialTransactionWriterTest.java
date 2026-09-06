package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.finance.expense.SpendingNature;
import com.apps.deen_sa.v2.entity.FinancialTransactionEntity;
import com.apps.deen_sa.v2.entity.TransactionDraftEntity;
import com.apps.deen_sa.v2.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.v2.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FinancialTransactionWriterTest {

    @Test
    void savesSpendingNatureFromTaxonomyInsteadOfExtraction() {
        FinancialTransactionRepository repository = mock(FinancialTransactionRepository.class);
        FinancialTransactionWriter writer = new FinancialTransactionWriter(
                repository, new ExpenseTaxonomyRegistry());

        TransactionDraftEntity draft = new TransactionDraftEntity();
        draft.setUser(new AppUserEntity());
        TransactionDraftExtractionEntity extraction = new TransactionDraftExtractionEntity();
        extraction.setDraft(draft);
        extraction.setAmount(new BigDecimal("120.00"));
        extraction.setOccurredAt(LocalDate.of(2026, 9, 6));
        extraction.setCategoryId("Food & Dining");
        extraction.setSubcategoryId("Groceries");

        writer.save(extraction, null);

        ArgumentCaptor<FinancialTransactionEntity> saved =
                ArgumentCaptor.forClass(FinancialTransactionEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSpendingNature()).isEqualTo(SpendingNature.ESSENTIAL);
    }
}
