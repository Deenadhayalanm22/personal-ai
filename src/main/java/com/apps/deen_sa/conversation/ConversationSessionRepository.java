package com.apps.deen_sa.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationSessionRepository extends JpaRepository<ConversationSessionEntity, Long> {
    Optional<ConversationSessionEntity> findByUserIdAndChannel(Long userId, String channel);

    @Modifying
    @Query(value = """
            UPDATE conversation_session
            SET active_draft_id = NULL, active_transaction_id = NULL, active_intent = NULL,
                waiting_for_field = NULL, partial_type = NULL, partial_json = NULL,
                pending_events_json = '[]'::jsonb, last_question = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId AND active_draft_id = :draftId
            """, nativeQuery = true)
    int clearPendingDraft(@Param("userId") Long userId, @Param("draftId") Long draftId);
}
