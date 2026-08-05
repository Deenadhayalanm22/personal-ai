package com.apps.deen_sa.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AudioConfirmationRepository extends JpaRepository<AudioConfirmationEntity, UUID> {

    @Modifying
    @Query("""
            update AudioConfirmationEntity confirmation
               set confirmation.status = :newStatus
             where confirmation.id = :id
               and confirmation.whatsappUserId = :whatsappUserId
               and confirmation.status = :expectedStatus
               and confirmation.expiresAt > :now
            """)
    int transition(UUID id, String whatsappUserId, AudioConfirmationStatus expectedStatus,
                   AudioConfirmationStatus newStatus, Instant now);

    Optional<AudioConfirmationEntity> findByIdAndWhatsappUserId(UUID id, String whatsappUserId);
}
