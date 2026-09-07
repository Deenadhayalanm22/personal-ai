package com.apps.deen_sa.service;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.conversation.AppUserService;
import com.apps.deen_sa.dto.InboundMessage;
import com.apps.deen_sa.dto.DraftWriteResult;
import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.domain.MessageSource;
import com.apps.deen_sa.entity.TransactionDraftEntity;
import com.apps.deen_sa.repository.TransactionDraftRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionDraftWriterTest {
    private final TransactionDraftRepository repository = mock(TransactionDraftRepository.class);
    private final AppUserService users = mock(AppUserService.class);
    private final TransactionDraftWriter writer = new TransactionDraftWriter(repository, users);

    @Test
    void insertsPendingDraftAndLoadsThePersistedRecord() {
        InboundMessage message = message();
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        TransactionDraftEntity persisted = new TransactionDraftEntity();
        persisted.setId(77L);
        when(users.resolve("WHATSAPP", "9198")).thenReturn(user);
        when(repository.findBySourceAndSourceMessageId(MessageSource.WHATSAPP, "wamid.1"))
                .thenReturn(Optional.empty(), Optional.of(persisted));

        when(repository.insertPendingIfAbsent(
                42L, "TEXT", "WHATSAPP", "wamid.1", "Paid ₹250")).thenReturn(1);

        assertThat(writer.saveAndCommit(message))
                .isEqualTo(new DraftWriteResult(77L, true));

        verify(repository).insertPendingIfAbsent(
                42L, "TEXT", "WHATSAPP", "wamid.1", "Paid ₹250");
    }

    @Test
    void duplicateDeliveryReturnsExistingDraftWithoutWriting() {
        TransactionDraftEntity persisted = new TransactionDraftEntity();
        persisted.setId(77L);
        when(repository.findBySourceAndSourceMessageId(MessageSource.WHATSAPP, "wamid.1"))
                .thenReturn(Optional.of(persisted));

        assertThat(writer.saveAndCommit(message()))
                .isEqualTo(new DraftWriteResult(77L, false));

        verify(users, never()).resolve("WHATSAPP", "9198");
        verify(repository, never()).insertPendingIfAbsent(
                42L, "TEXT", "WHATSAPP", "wamid.1", "Paid ₹250");
    }

    @Test
    void createsNewDraftForDistinctMessageWhileAnotherDraftIsPending() {
        InboundMessage nextMessage = new InboundMessage(
                "9198", "wamid.next-1", InputType.TEXT,
                MessageSource.WHATSAPP, "HI");
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        TransactionDraftEntity persisted = new TransactionDraftEntity();
        persisted.setId(78L);
        when(repository.findBySourceAndSourceMessageId(
                MessageSource.WHATSAPP, "wamid.next-1"))
                .thenReturn(Optional.empty(), Optional.of(persisted));
        when(users.resolve("WHATSAPP", "9198")).thenReturn(user);
        when(repository.insertPendingIfAbsent(
                42L, "TEXT", "WHATSAPP", "wamid.next-1", "HI")).thenReturn(1);

        assertThat(writer.routeAndCommit(nextMessage))
                .isEqualTo(new DraftWriteResult(78L, true));

        verify(repository).insertPendingIfAbsent(
                42L, "TEXT", "WHATSAPP", "wamid.next-1", "HI");
    }

    private InboundMessage message() {
        return new InboundMessage(
                "9198", "wamid.1", InputType.TEXT, MessageSource.WHATSAPP, "Paid ₹250");
    }
}
