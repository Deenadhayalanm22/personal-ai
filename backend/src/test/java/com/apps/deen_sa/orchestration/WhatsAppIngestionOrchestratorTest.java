package com.apps.deen_sa.orchestration;

import com.apps.deen_sa.dto.InboundMessage;
import com.apps.deen_sa.dto.DraftWriteResult;
import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.domain.MessageSource;
import com.apps.deen_sa.dto.WhatsAppWebhookPayload;
import com.apps.deen_sa.service.TransactionDraftWriter;
import com.apps.deen_sa.normalization.ExpenseNormalizationHandler;
import com.apps.deen_sa.whatsapp.WhatsAppInboundMessageMapper;
import com.apps.deen_sa.whatsapp.WhatsAppExpenseConfirmationCommandMapper;
import com.apps.deen_sa.service.ExpenseConfirmationCommandHandler;
import com.apps.deen_sa.service.WhatsAppAggregateBackfillCommandHandler;
import com.apps.deen_sa.service.FirstWhatsAppMessageAggregationTrigger;
import com.apps.deen_sa.whatsapp.WhatsAppAudioReviewHandler;
import com.apps.deen_sa.whatsapp.WhatsAppExpenseRecordedNotifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

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
        WhatsAppAggregateBackfillCommandHandler aggregateBackfillCommandHandler =
                mock(WhatsAppAggregateBackfillCommandHandler.class);
        FirstWhatsAppMessageAggregationTrigger aggregationTrigger =
                mock(FirstWhatsAppMessageAggregationTrigger.class);
        WhatsAppAudioReviewHandler audioReview = mock(WhatsAppAudioReviewHandler.class);
        WhatsAppWebhookPayload payload = new WhatsAppWebhookPayload(List.of());
        InboundMessage first = message("wamid.1");
        InboundMessage second = message("wamid.2");
        when(mapper.map(payload)).thenReturn(List.of(first, second));
        when(commandMapper.map(payload)).thenReturn(List.of());
        when(writer.routeAndCommit(first)).thenReturn(new DraftWriteResult(1L, true));
        when(writer.routeAndCommit(second)).thenReturn(new DraftWriteResult(2L, true));

        new WhatsAppIngestionOrchestrator(
                mapper, writer, normalizer, commandMapper, commandHandler,
                recordedNotifier, aggregateBackfillCommandHandler, aggregationTrigger, audioReview).ingest(payload);

        var ordered = inOrder(writer, normalizer);
        ordered.verify(writer).routeAndCommit(first);
        ordered.verify(normalizer).handle(new DraftWriteResult(1L, true), first);
        ordered.verify(writer).routeAndCommit(second);
        ordered.verify(normalizer).handle(new DraftWriteResult(2L, true), second);
        verify(aggregationTrigger, org.mockito.Mockito.times(2)).triggerIfNeeded();
    }

    @Test
    void duplicateDeliveryDoesNotStartDailyAggregation() {
        WhatsAppInboundMessageMapper mapper = mock(WhatsAppInboundMessageMapper.class);
        TransactionDraftWriter writer = mock(TransactionDraftWriter.class);
        ExpenseNormalizationHandler normalizer = mock(ExpenseNormalizationHandler.class);
        WhatsAppExpenseConfirmationCommandMapper commandMapper = mock(WhatsAppExpenseConfirmationCommandMapper.class);
        ExpenseConfirmationCommandHandler commandHandler = mock(ExpenseConfirmationCommandHandler.class);
        WhatsAppExpenseRecordedNotifier notifier = mock(WhatsAppExpenseRecordedNotifier.class);
        WhatsAppAggregateBackfillCommandHandler command = mock(WhatsAppAggregateBackfillCommandHandler.class);
        FirstWhatsAppMessageAggregationTrigger trigger = mock(FirstWhatsAppMessageAggregationTrigger.class);
        WhatsAppAudioReviewHandler audioReview = mock(WhatsAppAudioReviewHandler.class);
        WhatsAppWebhookPayload payload = new WhatsAppWebhookPayload(List.of());
        InboundMessage repeated = message("wamid.repeated");
        when(mapper.map(payload)).thenReturn(List.of(repeated));
        when(writer.routeAndCommit(repeated)).thenReturn(new DraftWriteResult(1L, false));

        new WhatsAppIngestionOrchestrator(mapper, writer, normalizer, commandMapper,
                commandHandler, notifier, command, trigger, audioReview).ingest(payload);

        verify(trigger, times(0)).triggerIfNeeded();
    }

    @Test
    void audioDraftIsStagedWithoutExpenseExtraction() {
        WhatsAppInboundMessageMapper mapper = mock(WhatsAppInboundMessageMapper.class);
        TransactionDraftWriter writer = mock(TransactionDraftWriter.class);
        ExpenseNormalizationHandler normalizer = mock(ExpenseNormalizationHandler.class);
        WhatsAppExpenseConfirmationCommandMapper commandMapper = mock(WhatsAppExpenseConfirmationCommandMapper.class);
        ExpenseConfirmationCommandHandler commandHandler = mock(ExpenseConfirmationCommandHandler.class);
        WhatsAppExpenseRecordedNotifier notifier = mock(WhatsAppExpenseRecordedNotifier.class);
        WhatsAppAggregateBackfillCommandHandler command = mock(WhatsAppAggregateBackfillCommandHandler.class);
        FirstWhatsAppMessageAggregationTrigger trigger = mock(FirstWhatsAppMessageAggregationTrigger.class);
        WhatsAppAudioReviewHandler audioReview = mock(WhatsAppAudioReviewHandler.class);
        WhatsAppWebhookPayload payload = new WhatsAppWebhookPayload(List.of());
        InboundMessage audio = new InboundMessage("9198", "wamid.audio",
                InputType.AUDIO, MessageSource.WHATSAPP, "media_id=123;mime_type=audio/ogg");
        DraftWriteResult draft = new DraftWriteResult(42L, true);
        when(mapper.map(payload)).thenReturn(List.of(audio));
        when(writer.routeAndCommit(audio)).thenReturn(draft);

        new WhatsAppIngestionOrchestrator(mapper, writer, normalizer, commandMapper,
                commandHandler, notifier, command, trigger, audioReview).ingest(payload);

        verify(audioReview).stage(draft, audio);
        org.mockito.Mockito.verifyNoInteractions(normalizer);
    }

    private InboundMessage message(String id) {
        return new InboundMessage(
                "9198", id, InputType.TEXT, MessageSource.WHATSAPP, "hello");
    }
}
