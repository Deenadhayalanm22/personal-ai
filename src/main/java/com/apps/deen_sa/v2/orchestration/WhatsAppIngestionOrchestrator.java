package com.apps.deen_sa.v2.orchestration;

import com.apps.deen_sa.v2.dto.WhatsAppWebhookPayload;
import com.apps.deen_sa.v2.normalization.ExpenseNormalizationHandler;
import com.apps.deen_sa.v2.service.TransactionDraftWriter;
import com.apps.deen_sa.v2.whatsapp.WhatsAppInboundMessageMapper;
import com.apps.deen_sa.v2.whatsapp.WhatsAppExpenseConfirmationCommandMapper;
import com.apps.deen_sa.v2.service.ExpenseConfirmationCommandHandler;
import com.apps.deen_sa.v2.whatsapp.WhatsAppExpenseRecordedNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class WhatsAppIngestionOrchestrator {

    private final WhatsAppInboundMessageMapper messageMapper;
    private final TransactionDraftWriter draftWriter;
    private final ExpenseNormalizationHandler normalizationHandler;
    private final WhatsAppExpenseConfirmationCommandMapper confirmationCommandMapper;
    private final ExpenseConfirmationCommandHandler confirmationCommandHandler;
    private final WhatsAppExpenseRecordedNotifier recordedNotifier;

    public void ingest(WhatsAppWebhookPayload payload) {
        var confirmationCommands = confirmationCommandMapper.map(payload);
        var messages = messageMapper.map(payload);
        log.info("Starting WhatsApp ingestion: confirmationCommands={}, messages={}",
                confirmationCommands.size(), messages.size());

        confirmationCommands.stream()
                .map(confirmationCommandHandler::handle)
                .filter(java.util.Objects::nonNull)
                .forEach(recordedNotifier::notify);
        messages.forEach(message -> {
            log.info("Processing WhatsApp message: messageId={}, inputType={}",
                    message.sourceMessageId(), message.inputType());
            var committedDraft = draftWriter.routeAndCommit(message);
            log.info("WhatsApp message routed: messageId={}, draftId={}, created={}",
                    message.sourceMessageId(), committedDraft.draftId(), committedDraft.created());
            normalizationHandler.handle(committedDraft, message);
        });
        log.info("Completed WhatsApp ingestion: confirmationCommands={}, messages={}",
                confirmationCommands.size(), messages.size());
    }
}
