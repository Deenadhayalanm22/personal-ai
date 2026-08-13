package com.apps.deen_sa.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Log4j2
public class WhatsAppMessageProcessor {

    private final Object[] userLocks = IntStream.range(0, 64).mapToObj(ignored -> new Object()).toArray();

    private final ConversationChannelGateway conversation;
    private final InboundMessageService inboundMessageService;
    private final WhatsAppReplySender replySender;
    private final WhatsAppMediaDownloader mediaDownloader;
    private final AudioTranscriber audioHandler;
    private final AudioConfirmationService confirmationService;
    private final UserFeatureFlagService featureFlags;
    private final WhatsAppAccessCommandService accessCommands;

    private static final String ACCESS_DENIED_MESSAGE =
            "Access is not enabled for this mobile number. Please contact the administrator.";

    @Async("whatsappExecutor")
    public void processIncomingMessage(String from, String text, String messageId) {

        Long inboundId = inboundMessageService.claim("WHATSAPP", messageId, from);
        if (messageId != null && inboundId == null) return;

        if (!hasFeatureAccess(from, inboundId)) return;

        try {
            var adminReply = accessCommands.execute(from, text);
            if (adminReply.isPresent()) {
                replySender.sendTextReply(from, adminReply.get());
                inboundMessageService.complete(inboundId);
                return;
            }
            processText(from, text, messageId);
            inboundMessageService.complete(inboundId);

        } catch (Exception e) {
            inboundMessageService.fail(inboundId);
            log.error("Failed to process WhatsApp text message {} from {}", messageId, from, e);
            // Never let async failure kill future messages
            replySender.sendTextReply(
                    from,
                    "Something went wrong. Please try again. If it keeps happening, take a screenshot and send it to the owner of this app."
            );
        }
    }

    @Async("whatsappExecutor")
    public void processIncomingAudio(String from, String mediaId, String mimeType, String messageId) {
        Long inboundId = inboundMessageService.claim("WHATSAPP", messageId, from);
        if (messageId != null && inboundId == null) return;
        if (!hasFeatureAccess(from, inboundId)) return;
        try {
            byte[] audio = mediaDownloader.download(mediaId);
            String transcription = audioHandler.transcribe(audio, mimeType);

            if (transcription == null || transcription.isBlank()) {
                replySender.sendTextReply(from, "I could not understand that voice note. Please try again.");
                return;
            }

            log.info("Transcribed WhatsApp voice note {} from {} as {}", mediaId, from, transcription);
            AudioConfirmationEntity confirmation = confirmationService.create(from, mediaId, transcription);
            replySender.sendAudioConfirmation(from, transcription, confirmation.getId().toString());
            inboundMessageService.complete(inboundId);
        } catch (Exception e) {
            inboundMessageService.fail(inboundId);
            log.error("Failed to process WhatsApp voice note {} from {}", mediaId, from, e);
            replySender.sendTextReply(from, "I could not transcribe that voice note. Please try again.");
        }
    }

    @Async("whatsappExecutor")
    public void processInteractiveReply(String from, String buttonId, String messageId) {
        if (buttonId == null) return;

        Long inboundId = inboundMessageService.claim("WHATSAPP", messageId, from);
        if (messageId != null && inboundId == null) return;
        if (!hasFeatureAccess(from, inboundId)) return;

        try {
            if (buttonId.startsWith("audio_confirm:")) {
                confirmAudio(from, confirmationId(buttonId, "audio_confirm:"));
            } else if (buttonId.startsWith("audio_retry:")) {
                retryAudio(from, confirmationId(buttonId, "audio_retry:"));
            } else if (buttonId.startsWith("answer:")) {
                processTrustedAnswer(from, buttonId.substring("answer:".length()), messageId);
            } else if ("control:skip".equals(buttonId)) {
                processText(from, "skip", messageId);
            } else if ("control:cancel".equals(buttonId)) {
                processText(from, "cancel", messageId);
            }
            inboundMessageService.complete(inboundId);
        } catch (IllegalArgumentException e) {
            inboundMessageService.fail(inboundId);
            log.warn("Ignoring invalid WhatsApp audio confirmation button {} from {}", buttonId, from);
            replySender.sendTextReply(from, "That confirmation is invalid or has expired. Please send the voice note again.");
        }
    }

    private void confirmAudio(String from, UUID confirmationId) {
        AudioConfirmationEntity confirmation = confirmationService.claim(confirmationId, from)
                .orElse(null);
        if (confirmation == null) {
            replySender.sendTextReply(from, "That confirmation has expired or was already handled. Please send the voice note again.");
            return;
        }

        try {
            processText(from, confirmation.getTranscribedText(), confirmationId.toString());
            confirmationService.complete(confirmationId);
        } catch (Exception e) {
            confirmationService.release(confirmationId);
            log.error("Failed to process confirmed audio transcription {} from {}", confirmationId, from, e);
            replySender.sendTextReply(from, "Something went wrong. Please tap Yes again. If it keeps happening, take a screenshot and send it to the owner of this app.");
        }
    }

    private void retryAudio(String from, UUID confirmationId) {
        if (confirmationService.reject(confirmationId, from)) {
            replySender.sendTextReply(from, "Okay, please record and send the voice note again.");
        } else {
            replySender.sendTextReply(from, "That confirmation has expired or was already handled. Please send the voice note again.");
        }
    }

    private UUID confirmationId(String buttonId, String prefix) {
        return UUID.fromString(buttonId.substring(prefix.length()));
    }

    private boolean hasFeatureAccess(String from, Long inboundId) {
        if (featureFlags.hasAnyEnabledFeature("WHATSAPP", from)) return true;

        log.info("Blocked WhatsApp message from {} because no feature is enabled", from);
        replySender.sendTextReply(from, ACCESS_DENIED_MESSAGE);
        inboundMessageService.complete(inboundId);
        return false;
    }

    private void processText(String from, String text, String messageId) {
        Object lock = userLocks[Math.floorMod(from.hashCode(), userLocks.length)];
        synchronized (lock) {
            log.info("Received message - {} from {}", text, from);
            SpeechResult result = conversation.process("WHATSAPP", from, messageId, text);

            log.info("Processed message - {} from {} and reply is ready - {}", text, from, result.getMessage());
            deliver(from, result);
        }
    }

    private void processTrustedAnswer(String from, String answer, String messageId) {
        Object lock = userLocks[Math.floorMod(from.hashCode(), userLocks.length)];
        synchronized (lock) {
            SpeechResult result = conversation.processTrustedAnswer("WHATSAPP", from, messageId, answer);
            deliver(from, result);
        }
    }

    private void deliver(String to, SpeechResult result) {
        if (result.getMedia() != null) {
            if (!replySender.sendImageReply(to, result.getMedia(), result.getMessage()) && result.getMessage() != null)
                replySender.sendTextReply(to, result.getMessage());
            return;
        }
        if (result.getMessage() == null) return;
        if (result.getActions() != null && !result.getActions().isEmpty())
            replySender.sendInteractiveReply(to, result.getMessage(), result.getActions());
        else replySender.sendTextReply(to, result.getMessage());
    }
}
