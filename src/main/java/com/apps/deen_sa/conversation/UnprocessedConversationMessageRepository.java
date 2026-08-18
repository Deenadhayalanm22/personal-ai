package com.apps.deen_sa.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UnprocessedConversationMessageRepository extends JpaRepository<UnprocessedConversationMessageEntity, Long> {
    Optional<UnprocessedConversationMessageEntity> findByChannelAndExternalMessageId(String channel, String externalMessageId);
}
