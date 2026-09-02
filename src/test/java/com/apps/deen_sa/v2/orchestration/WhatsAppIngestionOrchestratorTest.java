package com.apps.deen_sa.v2.orchestration;

import com.apps.deen_sa.v2.dto.InboundMessage;
import com.apps.deen_sa.v2.dto.DraftWriteResult;
import com.apps.deen_sa.v2.domain.InputType;
import com.apps.deen_sa.v2.domain.MessageSource;
import com.apps.deen_sa.v2.dto.WhatsAppWebhookPayload;
import com.apps.deen_sa.v2.service.TransactionDraftWriter;
import com.apps.deen_sa.v2.normalization.ExpenseNormalizationHandler;
import com.apps.deen_sa.v2.whatsapp.WhatsAppInboundMessageMapper;
import com.apps.deen_sa.v2.whatsapp.WhatsAppExpenseConfirmationCommandMapper;
import com.apps.deen_sa.v2.service.ExpenseConfirmationCommandHandler;
import com.apps.deen_sa.v2.whatsapp.WhatsAppExpenseRecordedNotifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WhatsAppIngestionOrchestratorTest {
    @Test
    void savesAndCommitsEachMappedMessageInOrder() {
        WhatsAppInboundMessageMapper mapper = mock(WhatsAppInboundMessageMapper.class);
        TransactionDraftWriter writer = mock(TransactionDraftWriter.class);
        ExpenseNormalizationHandler normalizer = mock(ExpenseNormalizationHandler.class);
        WhatsAppExpenseConfirmationCommandMapper commandMapper =
                mock(WhatsAppExpenseConfirmationCommandMapper.class);
        ExpenseConfirmationCommandHandler commandHandler =
                mock(ExpenseConfirmationCommandHandler.class);
        WhatsAppExpenseRecordedNotifier recordedNotifier =
                mock(WhatsAppExpenseRecordedNotifier.class);
        WhatsAppWebhookPayload payload = new WhatsAppWebhookPayload(List.of());
        InboundMessage first = message("wamid.1");
        InboundMessage second = message("wamid.2");
        when(mapper.map(payload)).thenReturn(List.of(first, second));
        when(commandMapper.map(payload)).thenReturn(List.of());
        when(writer.routeAndCommit(first)).thenReturn(new DraftWriteResult(1L, true));
        when(writer.routeAndCommit(second)).thenReturn(new DraftWriteResult(2L, true));

        new WhatsAppIngestionOrchestrator(
                mapper, writer, normalizer, commandMapper, commandHandler,
                recordedNotifier).ingest(payload);

        var ordered = inOrder(writer, normalizer);
        ordered.verify(writer).routeAndCommit(first);
        ordered.verify(normalizer).handle(new DraftWriteResult(1L, true), first);
        ordered.verify(writer).routeAndCommit(second);
        ordered.verify(normalizer).handle(new DraftWriteResult(2L, true), second);
    }

    private InboundMessage message(String id) {
        return new InboundMessage(
                "9198", id, InputType.TEXT, MessageSource.WHATSAPP, "hello");
    }
}
