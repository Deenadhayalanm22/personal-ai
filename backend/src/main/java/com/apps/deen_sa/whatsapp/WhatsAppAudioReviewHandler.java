package com.apps.deen_sa.whatsapp;

import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.domain.MessageSource;
import com.apps.deen_sa.dto.DraftWriteResult;
import com.apps.deen_sa.dto.InboundMessage;
import com.apps.deen_sa.dto.ResponseAction;
import com.apps.deen_sa.dto.WhatsAppWebhookPayload;
import com.apps.deen_sa.normalization.ExpenseNormalizationHandler;
import com.apps.deen_sa.service.AudioDraftReviewStore;
import com.apps.deen_sa.service.WhatsAppReplySender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/** FIN-EPIC-001 — Conversational expense capture. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppAudioReviewHandler {
    private static final String CONFIRM = "v2:audio:confirm:";
    private static final String DISCARD = "v2:audio:discard:";
    private final WhatsAppAudioTranscriber transcriber;
    private final AudioDraftReviewStore drafts;
    private final WhatsAppReplySender replies;
    private final ExpenseNormalizationHandler normalization;

    public void stage(DraftWriteResult draft, InboundMessage message) {
        if (!draft.created()) return;
        try {
            String raw = message.rawContent();
            String mediaId = raw.substring("media_id=".length(), raw.indexOf(';'));
            String words = transcriber.transcribe(mediaId);
            if (words.length() > 850) throw new IllegalArgumentException("Audio transcript is too long to review");
            if (drafts.stage(draft.draftId(), words)) {
                replies.sendInteractiveReply(message.externalUserId(),
                        "I heard:\n\n“" + words + "”\n\nAre these words correct? Confirm to review the expense details, or discard this voice note.",
                        List.of(new ResponseAction(CONFIRM + draft.draftId(), "Confirm words"),
                                new ResponseAction(DISCARD + draft.draftId(), "Discard")));
            }
        } catch (RuntimeException failure) {
            log.warn("Could not transcribe WhatsApp audio draft {}", draft.draftId(), failure);
            replies.sendTextReply(message.externalUserId(),
                    "I couldn't transcribe that voice note. Please send it again or type the expense.");
        }
    }

    public boolean handleReply(WhatsAppWebhookPayload.Message message) {
        if (!"interactive".equals(message.type()) || message.interactive() == null) return false;
        String replyId = message.interactive().replyId();
        if (replyId == null || !(replyId.startsWith(CONFIRM) || replyId.startsWith(DISCARD))) return false;
        boolean approve = replyId.startsWith(CONFIRM);
        String suffix = replyId.substring((approve ? CONFIRM : DISCARD).length());
        try {
            long draftId = Long.parseLong(suffix);
            var approved = drafts.review(draftId, message.from(), approve);
            if (approved != null) {
                normalization.handle(new DraftWriteResult(approved.draftId(), true),
                        new InboundMessage(message.from(), approved.sourceMessageId(),
                                InputType.TEXT, MessageSource.WHATSAPP, approved.text()));
            }
        } catch (NumberFormatException invalidId) {
            log.warn("Invalid audio review reply ID");
        } catch (IllegalArgumentException invalidDraft) {
            log.warn("Rejected audio review reply for draft {}", suffix);
        }
        return true;
    }
}
