package com.apps.deen_sa.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "inbound_message", uniqueConstraints =
        @UniqueConstraint(name = "uq_inbound_channel_message", columnNames = {"channel", "external_message_id"}))
@Getter
@Setter
public class InboundMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String channel;
    @Column(name = "external_message_id", nullable = false)
    private String externalMessageId;
    @Column(name = "external_user_id", nullable = false)
    private String externalUserId;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    private Instant processedAt;
}
