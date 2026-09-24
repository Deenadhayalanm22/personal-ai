package com.apps.deen_sa.whatsapp;

import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.domain.MessageSource;
import com.apps.deen_sa.dto.DraftWriteResult;
import com.apps.deen_sa.dto.InboundMessage;
import com.apps.deen_sa.dto.WhatsAppWebhookPayload;
import com.apps.deen_sa.normalization.ExpenseNormalizationHandler;
import com.apps.deen_sa.service.AudioDraftReviewStore;
import com.apps.deen_sa.service.WhatsAppReplySender;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppAudioReviewHandlerTest {
    private final WhatsAppAudioTranscriber transcriber = mock(WhatsAppAudioTranscriber.class);
    private final AudioDraftReviewStore drafts = mock(AudioDraftReviewStore.class);
    private final WhatsAppReplySender replies = mock(WhatsAppReplySender.class);
    private final ExpenseNormalizationHandler normalization = mock(ExpenseNormalizationHandler.class);
    private final WhatsAppAudioReviewHandler handler =
            new WhatsAppAudioReviewHandler(transcriber, drafts, replies, normalization);

    @Test
    void transcribedAudioShowsWordsAndWaitsForApproval() {
        when(transcriber.transcribe("12345")).thenReturn("Spent 250 at Swiggy");
        when(drafts.stage(42L, "Spent 250 at Swiggy")).thenReturn(true);
        handler.stage(new DraftWriteResult(42L, true), new InboundMessage(
                "9198", "wamid.audio", InputType.AUDIO, MessageSource.WHATSAPP,
                "media_id=12345;mime_type=audio/ogg"));

        verify(replies).sendInteractiveReply(eq("9198"), contains("Spent 250 at Swiggy"),
                argThat(actions -> actions.size() == 2
                        && actions.get(0).id().equals("v2:audio:confirm:42")
                        && actions.get(1).id().equals("v2:audio:discard:42")));
        verifyNoInteractions(normalization);
    }

    @Test
    void approvedWordsEnterTheExistingNormalizationFlow() {
        when(drafts.review(42L, "9198", true)).thenReturn(
                new AudioDraftReviewStore.ApprovedTranscript(42L, "wamid.audio", "Spent 250 at Swiggy"));
        var reply = new WhatsAppWebhookPayload.Message("wamid.reply", "9198", "interactive", null, null,
                new WhatsAppWebhookPayload.Interactive("button_reply",
                        new WhatsAppWebhookPayload.ButtonReply("v2:audio:confirm:42", "Confirm words"), null));

        org.assertj.core.api.Assertions.assertThat(handler.handleReply(reply)).isTrue();
        verify(normalization).handle(new DraftWriteResult(42L, true), new InboundMessage(
                "9198", "wamid.audio", InputType.TEXT, MessageSource.WHATSAPP,
                "Spent 250 at Swiggy"));
    }

    @Test
    void discardedWordsNeverReachNormalization() {
        var reply = new WhatsAppWebhookPayload.Message("wamid.reply", "9198", "interactive", null, null,
                new WhatsAppWebhookPayload.Interactive("button_reply",
                        new WhatsAppWebhookPayload.ButtonReply("v2:audio:discard:42", "Discard"), null));

        org.assertj.core.api.Assertions.assertThat(handler.handleReply(reply)).isTrue();
        verify(drafts).review(42L, "9198", false);
        verifyNoInteractions(normalization);
    }
}
