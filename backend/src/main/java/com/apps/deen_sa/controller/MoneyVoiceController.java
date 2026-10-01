package com.apps.deen_sa.controller;

import com.apps.deen_sa.insights.MoneyVoiceTranscriptionService;
import com.apps.deen_sa.service.WebAuthenticationService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** FIN-EPIC-003: authenticated audio upload returning a transcript for explicit user review. */
@RestController
@RequestMapping("/api/web/expense-chat")
public class MoneyVoiceController {
    private final WebAuthenticationService authentication;
    private final MoneyVoiceTranscriptionService voice;
    public MoneyVoiceController(WebAuthenticationService authentication, MoneyVoiceTranscriptionService voice) {
        this.authentication = authentication; this.voice = voice;
    }
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MoneyVoiceTranscriptionService.Transcript> transcribe(
            @CookieValue(name = "WEB_SESSION", required = false) String token,
            @RequestPart(name = "audio", required = false) MultipartFile audio) {
        var user = authentication.authenticate(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(voice.transcribe(user.getId(), audio));
    }
}
