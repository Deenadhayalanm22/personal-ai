package com.apps.deen_sa.v2.whatsapp;

import com.apps.deen_sa.conversation.WhatsAppReplySender;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class WhatsAppExpenseConfirmationAdapterTest {
    @Test
    void sendsOneTextInstructionForANonExpenseMessage() {
        WhatsAppReplySender replies = mock(WhatsAppReplySender.class);
        WhatsAppExpenseConfirmationAdapter adapter =
                new WhatsAppExpenseConfirmationAdapter(replies);

        adapter.sendExpenseInstruction("9198");

        verify(replies).sendTextReply(
                "9198",
                "To record an expense, send the amount and what it was for. Example: Spent ₹250 at Swiggy.");
        verifyNoMoreInteractions(replies);
    }
}
