package com.apps.deen_sa.orchestration;

import com.apps.deen_sa.dto.WhatsAppWebhookPayload;
import com.apps.deen_sa.normalization.ExpenseNormalizationHandler;
import com.apps.deen_sa.service.TransactionDraftWriter;
import com.apps.deen_sa.whatsapp.WhatsAppInboundMessageMapper;
import com.apps.deen_sa.whatsapp.WhatsAppExpenseConfirmationCommandMapper;
import com.apps.deen_sa.service.ExpenseConfirmationCommandHandler;
import com.apps.deen_sa.service.WhatsAppAggregateBackfillCommandHandler;
import com.apps.deen_sa.whatsapp.WhatsAppExpenseRecordedNotifier;
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
    private final WhatsAppAggregateBackfillCommandHandler aggregateBackfillCommandHandler;

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
            if (aggregateBackfillCommandHandler.handleIfSupported(message)) {
                log.info("Handled WhatsApp administration command: messageId={}",
                        message.sourceMessageId());
                return;
            }
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
