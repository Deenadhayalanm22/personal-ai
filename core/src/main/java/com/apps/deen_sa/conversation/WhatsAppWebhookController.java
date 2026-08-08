package com.apps.deen_sa.conversation;

import com.apps.deen_sa.dto.WhatsAppWebhookPayload;
import com.apps.deen_sa.conversation.WhatsAppMessageProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook/whatsapp")
@RequiredArgsConstructor
@Log4j2
public class WhatsAppWebhookController {

    private final WhatsAppMessageProcessor messageProcessor;

    // 🔹 1. Verification endpoint (GET)
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge
    ) {

        // keep this token in config, not hardcoded
        if ("subscribe".equals(mode) && "my-tellme-app-token".equals(token)) {
            return ResponseEntity.ok(challenge);
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    // 🔹 2. Incoming message endpoint (POST)
    @PostMapping
    public ResponseEntity<Void> receiveMessage(@RequestBody WhatsAppWebhookPayload payload) {

        log.info("Received message - {}", payload);

        payload.extractUserMessages().forEach(msg -> {
            messageProcessor.processIncomingMessage(
                    msg.from(),
                    msg.text(),
                    msg.messageId()
            );
        });

        payload.extractAudioMessages().forEach(msg ->
                messageProcessor.processIncomingAudio(
                        msg.from(),
                        msg.mediaId(),
                        msg.mimeType(),
                        msg.messageId()
                ));

        payload.extractInteractiveMessages().forEach(msg ->
                messageProcessor.processInteractiveReply(msg.from(), msg.buttonId(), msg.messageId()));

        // IMMEDIATE response to Meta
        return ResponseEntity.ok().build();
    }
}
