package com.apps.deen_sa.controller;

import com.apps.deen_sa.dto.WhatsAppWebhookPayload;
import com.apps.deen_sa.orchestrator.ConversationContext;
import com.apps.deen_sa.orchestrator.SpeechOrchestrator;
import com.apps.deen_sa.orchestrator.SpeechResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private final SpeechOrchestrator orchestrator;
    private final ConversationContext conversationContext;

    // 🔹 1. Verification endpoint (GET)
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge
    ) {

        // keep this token in config, not hardcoded
        if ("subscribe".equals(mode) && "MY_VERIFY_TOKEN".equals(token)) {
            return ResponseEntity.ok(challenge);
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    // 🔹 2. Incoming message endpoint (POST)
    @PostMapping
    public ResponseEntity<Void> receiveMessage(@RequestBody WhatsAppWebhookPayload payload) {

        payload.extractUserMessages().forEach(msg -> {

            SpeechResult result = orchestrator.process(
                    msg.text(),
                    conversationContext
            );

            // TODO:
            // send result.message() back to WhatsApp via Meta Send API
            // async, never block webhook thread
        });

        // Meta expects 200 OK, nothing else
        return ResponseEntity.ok().build();
    }
}
