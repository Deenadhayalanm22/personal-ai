package com.apps.deen_sa.conversation;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechOrchestrator;
import com.apps.deen_sa.conversation.SpeechResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class WhatsAppMessageProcessor {

    private final SpeechOrchestrator orchestrator;
    private final ConversationContext conversationContext;
    private final WhatsAppReplySender replySender;
    private final WhatsAppMediaDownloader mediaDownloader;
    private final AudioHandler audioHandler;
    private final AudioConfirmationService confirmationService;

    @Async("whatsappExecutor")
    public void processIncomingMessage(String from, String text) {

        try {
            processText(from, text);

        } catch (Exception e) {
            // Never let async failure kill future messages
            replySender.sendTextReply(
                    from,
                    "Something went wrong. Please try again."
            );
        }
    }

    @Async("whatsappExecutor")
    public void processIncomingAudio(String from, String mediaId, String mimeType) {
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
        } catch (Exception e) {
            log.error("Failed to process WhatsApp voice note {} from {}", mediaId, from, e);
            replySender.sendTextReply(from, "I could not transcribe that voice note. Please try again.");
        }
    }

    @Async("whatsappExecutor")
    public void processInteractiveReply(String from, String buttonId) {
        if (buttonId == null) return;

        try {
            if (buttonId.startsWith("audio_confirm:")) {
                confirmAudio(from, confirmationId(buttonId, "audio_confirm:"));
            } else if (buttonId.startsWith("audio_retry:")) {
                retryAudio(from, confirmationId(buttonId, "audio_retry:"));
            }
        } catch (IllegalArgumentException e) {
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
        log.info("Received message - {} from {}", text, from);
        SpeechResult result = orchestrator.process(text, conversationContext);

        log.info("Processed message - {} from {} and reply is ready - {}", text, from, result.getMessage());
        if (result.getMessage() != null) {
            replySender.sendTextReply(from, result.getMessage());
        }
    }
}
