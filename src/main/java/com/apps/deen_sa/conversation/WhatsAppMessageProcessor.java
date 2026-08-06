package com.apps.deen_sa.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.IntStream;
import com.apps.deen_sa.conversation.interpretation.UnifiedConversationEngine;

@Service
@RequiredArgsConstructor
@Log4j2
public class WhatsAppMessageProcessor {

    private final Object[] userLocks = IntStream.range(0, 64).mapToObj(ignored -> new Object()).toArray();

    private final AppUserService appUserService;
    private final ConversationSessionService sessionService;
    private final InboundMessageService inboundMessageService;
    private final WhatsAppReplySender replySender;
    private final WhatsAppMediaDownloader mediaDownloader;
    private final AudioHandler audioHandler;
    private final AudioConfirmationService confirmationService;
    private final UnifiedConversationEngine unifiedEngine;

    @Async("whatsappExecutor")
    public void processIncomingMessage(String from, String text, String messageId) {

        Long inboundId = inboundMessageService.claim("WHATSAPP", messageId, from);
        if (messageId != null && inboundId == null) return;

        try {
            processText(from, text);
            inboundMessageService.complete(inboundId);

        } catch (Exception e) {
            inboundMessageService.fail(inboundId);
            log.error("Failed to process WhatsApp text message {} from {}", messageId, from, e);
            // Never let async failure kill future messages
            replySender.sendTextReply(
                    from,
                    "Something went wrong. Please try again."
            );
        }
    }

    @Async("whatsappExecutor")
    public void processIncomingAudio(String from, String mediaId, String mimeType, String messageId) {
        Long inboundId = inboundMessageService.claim("WHATSAPP", messageId, from);
        if (messageId != null && inboundId == null) return;
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

        try {
            if (buttonId.startsWith("audio_confirm:")) {
                confirmAudio(from, confirmationId(buttonId, "audio_confirm:"));
            } else if (buttonId.startsWith("audio_retry:")) {
                retryAudio(from, confirmationId(buttonId, "audio_retry:"));
            } else if (buttonId.startsWith("answer:")) {
                processTrustedAnswer(from, buttonId.substring("answer:".length()));
            } else if ("control:skip".equals(buttonId)) {
                processText(from, "skip");
            } else if ("control:cancel".equals(buttonId)) {
                processText(from, "cancel");
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
            processText(from, confirmation.getTranscribedText());
            confirmationService.complete(confirmationId);
        } catch (Exception e) {
            confirmationService.release(confirmationId);
            log.error("Failed to process confirmed audio transcription {} from {}", confirmationId, from, e);
            replySender.sendTextReply(from, "Something went wrong. Please tap Yes again.");
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

    private void processText(String from, String text) {
        Object lock = userLocks[Math.floorMod(from.hashCode(), userLocks.length)];
        synchronized (lock) {
            log.info("Received message - {} from {}", text, from);
            AppUserEntity user = appUserService.resolve("WHATSAPP", from);
            ConversationContext context = sessionService.load(user.getId(), "WHATSAPP");
            context.setTimezone(user.getTimezone());
            context.setLocale(user.getLocale());
            context.setCurrency(user.getCurrency());
            SpeechResult result = unifiedEngine.process(text, context);
            sessionService.save(context);

            log.info("Processed message - {} from {} and reply is ready - {}", text, from, result.getMessage());
            if (result.getMessage() != null) {
                if (result.getActions() != null && !result.getActions().isEmpty()) {
                    replySender.sendInteractiveReply(from, result.getMessage(), result.getActions());
                } else {
                    replySender.sendTextReply(from, result.getMessage());
                }
            }
        }
    }

    private void processTrustedAnswer(String from, String answer) {
        Object lock = userLocks[Math.floorMod(from.hashCode(), userLocks.length)];
        synchronized (lock) {
            AppUserEntity user = appUserService.resolve("WHATSAPP", from);
            ConversationContext context = sessionService.load(user.getId(), "WHATSAPP");
            context.setTimezone(user.getTimezone());
            context.setLocale(user.getLocale());
            context.setCurrency(user.getCurrency());
            SpeechResult result = unifiedEngine.processTrustedAnswer(answer, context);
            sessionService.save(context);
            if (result.getMessage() != null) {
                if (result.getActions() != null && !result.getActions().isEmpty()) {
                    replySender.sendInteractiveReply(from, result.getMessage(), result.getActions());
                } else {
                    replySender.sendTextReply(from, result.getMessage());
                }
            }
        }
    }
}
