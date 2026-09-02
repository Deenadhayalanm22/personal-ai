package com.apps.deen_sa.v2.controller;

import com.apps.deen_sa.v2.dto.WhatsAppWebhookPayload;
import com.apps.deen_sa.v2.orchestration.WhatsAppIngestionOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookV2Controller {

    private static final String VERIFY_TOKEN = "my-tellme-app-token";

    private final WhatsAppIngestionOrchestrator ingestionOrchestrator;

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
                                                @RequestParam("hub.mode") String mode,
                                                @RequestParam("hub.verify_token") String token,
                                                @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && VERIFY_TOKEN.equals(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<Void> receiveMessage(@RequestBody WhatsAppWebhookPayload payload) {
        ingestionOrchestrator.ingest(payload);
        return ResponseEntity.ok().build();
    }
}
