package com.apps.deen_sa.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import com.apps.deen_sa.conversation.interpretation.PendingEvent;
import com.apps.deen_sa.conversation.interpretation.ConversationTurn;

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
    private Long activeDraftId;
    private String activeIntent;
    private String waitingForField;
    private String partialType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> partialJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_events_json", columnDefinition = "jsonb")
    private List<PendingEvent> pendingEvents;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recent_turns_json", columnDefinition = "jsonb")
    private List<ConversationTurn> recentTurns;
    @Column(name = "last_question", columnDefinition = "TEXT")
    private String lastQuestion;
    @Column(name = "interpreter_version", length = 50)
    private String interpreterVersion;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
