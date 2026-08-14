package com.apps.deen_sa.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "conversation_diagnostic_turn")
@Getter
@Setter
public class ConversationDiagnosticTurnEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 30)
    private String channel;
    @Column(name = "external_user_id", nullable = false)
    private String externalUserId;
    @Column(name = "external_message_id")
    private String externalMessageId;
    @Column(name = "input_kind", nullable = false, length = 30)
    private String inputKind;
    @Column(name = "input_text", nullable = false, columnDefinition = "TEXT")
    private String inputText;
    @Column(name = "response_status", length = 30)
    private String responseStatus;
    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;
    @Column(name = "response_media_type", length = 100)
    private String responseMediaType;
    @Column(name = "response_media_filename")
    private String responseMediaFilename;
    @Column(name = "response_media_size")
    private Integer responseMediaSize;
    @Column(name = "need_followup")
    private Boolean needFollowup;
    @Column(name = "active_intent", length = 50)
    private String activeIntent;
    @Column(name = "waiting_for_field", length = 100)
    private String waitingForField;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "partial_json", columnDefinition = "jsonb")
    private Map<String, Object> partialJson;
    @Column(name = "saved_entity_type")
    private String savedEntityType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "saved_entity_json", columnDefinition = "jsonb")
    private Map<String, Object> savedEntityJson;
    @Column(nullable = false)
    private boolean reviewed;
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
