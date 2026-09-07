package com.apps.deen_sa.v2.whatsapp;

import com.apps.deen_sa.conversation.MagicLinkService;
import com.apps.deen_sa.conversation.WhatsAppReplySender;
import com.apps.deen_sa.v2.dto.RecordedExpense;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppExpenseRecordedNotifierTest {
    @Test
    void includesPortalLinkAfterRecordingExpense() {
        WhatsAppReplySender replies = mock(WhatsAppReplySender.class);
        MagicLinkService magicLinks = mock(MagicLinkService.class);
        when(magicLinks.portalUrl()).thenReturn("https://money.example.com/portal");
        WhatsAppExpenseRecordedNotifier notifier =
                new WhatsAppExpenseRecordedNotifier(replies, magicLinks);

        notifier.notify(new RecordedExpense("9198", new BigDecimal("922"), null));

        verify(replies).sendPortalLink(
                "9198",
                "Expense added successfully: ₹922.\n\n"
                        + "View or make changes: https://money.example.com/portal",
                "https://money.example.com/portal");
    }
}
