package com.apps.deen_sa.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "audio_confirmation")
public class AudioConfirmationEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String whatsappUserId;

    @Column(nullable = false)
    private String mediaId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String transcribedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AudioConfirmationStatus status;

    @Column(nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
