package com.apps.deen_sa.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "unprocessed_conversation_message")
@Getter @Setter
public class UnprocessedConversationMessageEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 30) private String channel;
    @Column(name = "external_message_id") private String externalMessageId;
    @Column(name = "message_text", nullable = false, columnDefinition = "text") private String messageText;
    private String locale;
    @Column(nullable = false, length = 80) private String reason;
    @Column(name = "interpreter_version") private String interpreterVersion;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "occurrence_count", nullable = false) private int occurrenceCount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
