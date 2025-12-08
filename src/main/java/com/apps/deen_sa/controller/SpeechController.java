package com.apps.deen_sa.controller;

import com.apps.deen_sa.dto.SpeechInput;
import com.apps.deen_sa.orchestrator.ConversationContext;
import com.apps.deen_sa.orchestrator.SpeechOrchestrator;
import com.apps.deen_sa.orchestrator.SpeechResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SpeechController {

    private final SpeechOrchestrator orchestrator;
    private final ConversationContext conversationContext;

    @PostMapping("/process")
    public SpeechResult processSpeech(@RequestBody SpeechInput request) {

        return orchestrator.process(
                request.getText(),
                conversationContext
        );
    }
}

