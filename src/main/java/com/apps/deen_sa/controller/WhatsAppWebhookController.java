package com.apps.deen_sa.controller;

import com.apps.deen_sa.dto.WhatsAppWebhookPayload;
import com.apps.deen_sa.orchestration.WhatsAppIngestionOrchestrator;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
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
@Log4j2
public class WhatsAppWebhookController {

    private final WhatsAppIngestionOrchestrator ingestionOrchestrator;
    private final String verifyToken;

    public WhatsAppWebhookController(
            WhatsAppIngestionOrchestrator ingestionOrchestrator,
            @Value("${whatsapp.verify-token:}") String verifyToken
    ) {
        this.ingestionOrchestrator = ingestionOrchestrator;
        this.verifyToken = verifyToken;
    }

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
                                                @RequestParam("hub.mode") String mode,
                                                @RequestParam("hub.verify_token") String token,
                                                @RequestParam("hub.challenge") String challenge) {
        if (verifyToken.isBlank()) {
            log.error("WhatsApp webhook verification rejected: WHATSAPP_VERIFY_TOKEN is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Webhook verification is not configured");
        }
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("WhatsApp webhook verification succeeded");
            return ResponseEntity.ok(challenge);
        }
        log.warn("WhatsApp webhook verification rejected: mode={}, tokenMatched={}",
                mode, verifyToken.equals(token));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<Void> receiveMessage(@RequestBody WhatsAppWebhookPayload payload) {
        int entryCount = payload.entry() == null ? 0 : payload.entry().size();
        log.info("WhatsApp webhook payload accepted by controller: entries={}", entryCount);
        try {
            ingestionOrchestrator.ingest(payload);
        } catch (RuntimeException failure) {
            log.error("WhatsApp webhook processing failed: entries={}", entryCount, failure);
            throw failure;
        }
        log.info("WhatsApp webhook processing completed: entries={}", entryCount);
        return ResponseEntity.ok().build();
    }
}
