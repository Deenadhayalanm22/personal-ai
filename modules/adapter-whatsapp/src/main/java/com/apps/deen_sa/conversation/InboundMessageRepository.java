package com.apps.deen_sa.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundMessageRepository extends JpaRepository<InboundMessageEntity, Long> {
    boolean existsByChannelAndExternalMessageId(String channel, String externalMessageId);
}
