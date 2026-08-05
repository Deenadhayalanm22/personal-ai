package com.apps.deen_sa.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "conversation_session", uniqueConstraints =
        @UniqueConstraint(name = "uq_conversation_user_channel", columnNames = {"user_id", "channel"}))
@Getter
@Setter
public class ConversationSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 30)
    private String channel;
    private Long activeTransactionId;
    private String activeIntent;
    private String waitingForField;
    private String partialType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> partialJson;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
