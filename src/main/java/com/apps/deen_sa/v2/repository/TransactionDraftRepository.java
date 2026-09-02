package com.apps.deen_sa.v2.repository;

import com.apps.deen_sa.v2.entity.TransactionDraftEntity;
import com.apps.deen_sa.v2.domain.MessageSource;
import com.apps.deen_sa.v2.domain.TransactionDraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface TransactionDraftRepository extends JpaRepository<TransactionDraftEntity, Long> {
    Optional<TransactionDraftEntity> findBySourceAndSourceMessageId(
            MessageSource source, String sourceMessageId);

    Optional<TransactionDraftEntity> findFirstByUserExternalUserIdAndUserChannelAndSourceAndStatusOrderByCreatedAtDesc(
            String externalUserId,
            String channel,
            MessageSource source,
            TransactionDraftStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT draft FROM TransactionDraftEntity draft WHERE draft.id = :draftId")
    Optional<TransactionDraftEntity> findByIdForUpdate(@Param("draftId") Long draftId);

    @Modifying
    @Query(value = """
            INSERT INTO transaction_draft
                (user_id, input_type, source, source_message_id, raw_text, status)
            VALUES
                (:userId, :inputType, :source, :messageId, :rawText, 'PENDING')
            ON CONFLICT (source, source_message_id) DO NOTHING
            """, nativeQuery = true)
    int insertPendingIfAbsent(
            @Param("userId") Long userId,
            @Param("inputType") String inputType,
            @Param("source") String source,
            @Param("messageId") String messageId,
            @Param("rawText") String rawText);
}
