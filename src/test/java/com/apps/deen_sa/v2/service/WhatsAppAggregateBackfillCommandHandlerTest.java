package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.conversation.UserFeatureFlagService;
import com.apps.deen_sa.conversation.WhatsAppReplySender;
import com.apps.deen_sa.v2.domain.InputType;
import com.apps.deen_sa.v2.domain.MessageSource;
import com.apps.deen_sa.v2.dto.InboundMessage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WhatsAppAggregateBackfillCommandHandlerTest {
    private final UserFeatureFlagService flags = mock(UserFeatureFlagService.class);
    private final ExpenseDailyAggregationService aggregation = mock(ExpenseDailyAggregationService.class);
    private final WhatsAppReplySender replies = mock(WhatsAppReplySender.class);
    private final WhatsAppAggregateBackfillCommandHandler handler =
            new WhatsAppAggregateBackfillCommandHandler(
                    flags, aggregation, replies,
                    Clock.fixed(Instant.parse("2026-09-06T19:30:00Z"), ZoneOffset.UTC),
                    "Asia/Kolkata");

    @Test
    void superAdminCommandBackfillsAllMissingDatesBeforeToday() {
        when(flags.isSuperAdmin("WHATSAPP", "9198")).thenReturn(true);
        when(aggregation.rebuildMissingBefore(LocalDate.of(2026, 9, 7)))
                .thenReturn(new ExpenseDailyAggregationService.BackfillResult(
                        List.of(LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 6)), 7));

        boolean handled = handler.handleIfSupported(message("/aggregate"));

        assertThat(handled).isTrue();
        verify(replies).sendTextReply("9198",
                "Expense aggregate backfill completed: 2 date(s), 7 aggregate row(s).");
    }

    @Test
    void rejectsTheCommandForNonAdminsWithoutRunningTheJob() {
        boolean handled = handler.handleIfSupported(message("/aggregate"));

        assertThat(handled).isTrue();
        verifyNoInteractions(aggregation);
        verify(replies).sendTextReply("9198",
                "This aggregation command is restricted to the super admin.");
    }

    @Test
    void ignoresOrdinaryMessages() {
        assertThat(handler.handleIfSupported(message("Spent 200 on groceries"))).isFalse();
        verifyNoInteractions(flags, aggregation, replies);
    }

    private InboundMessage message(String text) {
        return new InboundMessage("9198", "wamid.1", InputType.TEXT, MessageSource.WHATSAPP, text);
    }
}
