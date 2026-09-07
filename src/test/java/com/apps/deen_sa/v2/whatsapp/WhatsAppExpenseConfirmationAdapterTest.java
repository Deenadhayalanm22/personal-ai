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
                """
                I couldn't identify that as an expense.

                Use this format:
                Spent ₹[amount] for [purpose or merchant] using [account]

                Example:
                Spent ₹922 for electricity using HDFC credit card.

                Amount and purpose are required. Account is optional. The date defaults to today.
                """.strip());
        verifyNoMoreInteractions(replies);
    }
}
