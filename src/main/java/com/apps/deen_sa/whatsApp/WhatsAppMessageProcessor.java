package com.apps.deen_sa.whatsApp;

import com.apps.deen_sa.orchestrator.ConversationContext;
import com.apps.deen_sa.orchestrator.SpeechOrchestrator;
import com.apps.deen_sa.orchestrator.SpeechResult;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WhatsAppMessageProcessor {

    private final SpeechOrchestrator orchestrator;
    private final ConversationContext conversationContext;
    private final WhatsAppReplySender replySender;

    @Async("whatsappExecutor")
    public void processIncomingMessage(String from, String text) {

        try {
            SpeechResult result =
                    orchestrator.process(text, conversationContext);

            if (result.getMessage() != null) {
                replySender.sendTextReply(from, result.getMessage());
            }

        } catch (Exception e) {
            // Never let async failure kill future messages
            replySender.sendTextReply(
                    from,
                    "Something went wrong. Please try again."
            );
        }
    }
}
