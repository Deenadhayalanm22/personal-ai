package com.apps.deen_sa.v2.orchestration;

import com.apps.deen_sa.v2.dto.WhatsAppWebhookPayload;
import com.apps.deen_sa.v2.normalization.ExpenseNormalizationHandler;
import com.apps.deen_sa.v2.service.TransactionDraftWriter;
import com.apps.deen_sa.v2.whatsapp.WhatsAppInboundMessageMapper;
import com.apps.deen_sa.v2.whatsapp.WhatsAppExpenseConfirmationCommandMapper;
import com.apps.deen_sa.v2.service.ExpenseConfirmationCommandHandler;
import com.apps.deen_sa.v2.whatsapp.WhatsAppExpenseRecordedNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WhatsAppIngestionOrchestrator {

    private final WhatsAppInboundMessageMapper messageMapper;
    private final TransactionDraftWriter draftWriter;
    private final ExpenseNormalizationHandler normalizationHandler;
    private final WhatsAppExpenseConfirmationCommandMapper confirmationCommandMapper;
    private final ExpenseConfirmationCommandHandler confirmationCommandHandler;
    private final WhatsAppExpenseRecordedNotifier recordedNotifier;

    public void ingest(WhatsAppWebhookPayload payload) {
        confirmationCommandMapper.map(payload).stream()
                .map(confirmationCommandHandler::handle)
                .filter(java.util.Objects::nonNull)
                .forEach(recordedNotifier::notify);
        messageMapper.map(payload).forEach(message -> {
            var committedDraft = draftWriter.routeAndCommit(message);
            normalizationHandler.handle(committedDraft, message);
        });
    }
}
