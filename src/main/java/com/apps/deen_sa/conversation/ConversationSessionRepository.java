package com.apps.deen_sa.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ConversationSessionRepository extends JpaRepository<ConversationSessionEntity, Long> {
    Optional<ConversationSessionEntity> findByUserIdAndChannel(Long userId, String channel);
}
