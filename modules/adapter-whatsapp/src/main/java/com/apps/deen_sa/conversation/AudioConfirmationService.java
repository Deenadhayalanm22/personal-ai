package com.apps.deen_sa.conversation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AudioConfirmationService {

    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(15);

    private final AudioConfirmationRepository repository;

    public AudioConfirmationEntity create(String whatsappUserId, String mediaId, String text) {
        AudioConfirmationEntity confirmation = new AudioConfirmationEntity();
        confirmation.setId(UUID.randomUUID());
        confirmation.setWhatsappUserId(whatsappUserId);
        confirmation.setMediaId(mediaId);
        confirmation.setTranscribedText(text);
        confirmation.setStatus(AudioConfirmationStatus.PENDING);
        confirmation.setExpiresAt(Instant.now().plus(CONFIRMATION_TTL));
        return repository.save(confirmation);
    }

    @Transactional
    public Optional<AudioConfirmationEntity> claim(UUID id, String whatsappUserId) {
        int updated = repository.transition(id, whatsappUserId,
                AudioConfirmationStatus.PENDING, AudioConfirmationStatus.PROCESSING, Instant.now());
        return updated == 1 ? repository.findByIdAndWhatsappUserId(id, whatsappUserId) : Optional.empty();
    }

    @Transactional
    public boolean reject(UUID id, String whatsappUserId) {
        return repository.transition(id, whatsappUserId,
                AudioConfirmationStatus.PENDING, AudioConfirmationStatus.REJECTED, Instant.now()) == 1;
    }

    @Transactional
    public void complete(UUID id) {
        repository.findById(id).ifPresent(confirmation ->
                confirmation.setStatus(AudioConfirmationStatus.COMPLETED));
    }

    @Transactional
    public void release(UUID id) {
        repository.findById(id).ifPresent(confirmation -> {
            if (confirmation.getStatus() == AudioConfirmationStatus.PROCESSING) {
                confirmation.setStatus(AudioConfirmationStatus.PENDING);
            }
        });
    }
}
