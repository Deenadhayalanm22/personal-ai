package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.domain.MessageSource;
import com.apps.deen_sa.domain.TransactionDraftStatus;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.TransactionDraftEntity;
import com.apps.deen_sa.repository.TransactionDraftExtractionRepository;
import com.apps.deen_sa.repository.TransactionDraftRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AudioDraftReviewStoreTest {
    private final TransactionDraftRepository drafts = mock(TransactionDraftRepository.class);
    private final TransactionDraftExtractionRepository extractions = mock(TransactionDraftExtractionRepository.class);
    private final AudioDraftReviewStore store = new AudioDraftReviewStore(drafts, extractions);

    @Test
    void stagesAndApprovesWordsBeforeExpenseExtraction() {
        TransactionDraftEntity draft = audioDraft();
        when(drafts.findByIdForUpdate(42L)).thenReturn(Optional.of(draft));
        assertThat(store.stage(42L, "Spent 250 at Swiggy")).isTrue();
        assertThat(draft.getStatus()).isEqualTo(TransactionDraftStatus.TRANSCRIPT_REVIEW);
        assertThat(draft.getTranscribedText()).isEqualTo("Spent 250 at Swiggy");

        var approved = store.review(42L, "9198", true);
        assertThat(approved).isEqualTo(new AudioDraftReviewStore.ApprovedTranscript(
                42L, "wamid.audio", "Spent 250 at Swiggy"));
        assertThat(draft.getStatus()).isEqualTo(TransactionDraftStatus.PENDING);
    }

    @Test
    void discardAndForeignReplyCannotApproveAudio() {
        TransactionDraftEntity draft = audioDraft();
        draft.setStatus(TransactionDraftStatus.TRANSCRIPT_REVIEW);
        draft.setTranscribedText("Spent 250 at Swiggy");
        when(drafts.findByIdForUpdate(42L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> store.review(42L, "someone-else", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(draft.getStatus()).isEqualTo(TransactionDraftStatus.TRANSCRIPT_REVIEW);
        assertThat(store.review(42L, "9198", false)).isNull();
        assertThat(draft.getStatus()).isEqualTo(TransactionDraftStatus.CANCELLED);
        assertThat(store.review(42L, "9198", true)).isNull();
    }

    private TransactionDraftEntity audioDraft() {
        var user = new AppUserEntity();
        user.setExternalUserId("9198");
        var draft = new TransactionDraftEntity();
        draft.setId(42L);
        draft.setUser(user);
        draft.setInputType(InputType.AUDIO);
        draft.setSource(MessageSource.WHATSAPP);
        draft.setSourceMessageId("wamid.audio");
        return draft;
    }
}
