package com.apps.deen_sa.v2.repository;

import com.apps.deen_sa.v2.domain.TransactionDraftExtractionStatus;
import com.apps.deen_sa.v2.entity.TransactionDraftExtractionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface TransactionDraftExtractionRepository
        extends JpaRepository<TransactionDraftExtractionEntity, Long> {
    Optional<TransactionDraftExtractionEntity> findByDraftIdAndStatus(
            Long draftId, TransactionDraftExtractionStatus status);

    @Query("""
            SELECT extraction
            FROM TransactionDraftExtractionEntity extraction
            JOIN FETCH extraction.draft draft
            JOIN FETCH draft.user user
            WHERE extraction.id = :extractionId
              AND user.externalUserId = :externalUserId
              AND draft.source = com.apps.deen_sa.v2.domain.MessageSource.WHATSAPP
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TransactionDraftExtractionEntity> findOwnedWhatsAppExtraction(
            @Param("extractionId") Long extractionId,
            @Param("externalUserId") String externalUserId);
}
