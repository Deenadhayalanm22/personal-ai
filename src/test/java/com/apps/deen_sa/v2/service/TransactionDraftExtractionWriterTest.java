package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.v2.dto.NormalizedExpense;
import com.apps.deen_sa.v2.dto.StoredDraftExtraction;
import com.apps.deen_sa.v2.domain.TransactionDraftExtractionStatus;
import com.apps.deen_sa.v2.entity.TransactionDraftEntity;
import com.apps.deen_sa.v2.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.v2.repository.TransactionDraftExtractionRepository;
import com.apps.deen_sa.v2.repository.TransactionDraftRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionDraftExtractionWriterTest {
    private final TransactionDraftRepository drafts = mock(TransactionDraftRepository.class);
    private final TransactionDraftExtractionRepository extractions =
            mock(TransactionDraftExtractionRepository.class);
    private final TransactionDraftExtractionWriter writer =
            new TransactionDraftExtractionWriter(drafts, extractions);

    @Test
    void storesTheOnlyExtractionAttemptForDraft() {
        TransactionDraftEntity draft = draft();
        when(drafts.findByIdForUpdate(1001L)).thenReturn(Optional.of(draft));
        when(extractions.findByDraftIdAndStatus(
                1001L, TransactionDraftExtractionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(extractions.saveAndFlush(any())).thenAnswer(invocation -> {
            TransactionDraftExtractionEntity saved = invocation.getArgument(0);
            saved.setId(5002L);
            return saved;
        });

        StoredDraftExtraction saved = writer.saveActive(normalized());

        assertThat(saved.extractionId()).isEqualTo(5002L);
        assertThat(saved.categoryId()).isEqualTo("Food & Dining");
        assertThat(saved.subcategoryId()).isEqualTo("Groceries");
        assertThat(saved.confidence()).isEqualByComparingTo("0.94");
    }

    private TransactionDraftEntity draft() {
        AppUserEntity user = new AppUserEntity();
        user.setExternalUserId("9198");
        TransactionDraftEntity draft = new TransactionDraftEntity();
        draft.setId(1001L);
        draft.setUser(user);
        return draft;
    }

    private NormalizedExpense normalized() {
        return new NormalizedExpense(
                1001L, "9198", new BigDecimal("850"), "Food & Dining", "Groceries",
                "Star Bazaar", LocalDate.of(2026, 9, 1), new BigDecimal("0.94"));
    }
}
