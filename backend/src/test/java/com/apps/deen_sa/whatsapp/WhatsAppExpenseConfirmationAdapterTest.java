package com.apps.deen_sa.whatsapp;

import com.apps.deen_sa.service.WhatsAppReplySender;
import com.apps.deen_sa.insights.WebExpenseCaptureService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class WhatsAppExpenseConfirmationAdapterTest {
    @Test
    void incompleteExpenseReturnsSameStatementWithoutFollowup() {
        WhatsAppReplySender replies = mock(WhatsAppReplySender.class);
        new WhatsAppExpenseConfirmationAdapter(replies).sendCaptureInstruction("9198", WebExpenseCaptureService.INCOMPLETE);
        verify(replies).sendTextReply("9198", WebExpenseCaptureService.INCOMPLETE);
        verifyNoMoreInteractions(replies);
    }

    @Test
    void unsupportedRequestPolitelyDirectsUserToPortal() {
        WhatsAppReplySender replies = mock(WhatsAppReplySender.class);
        new WhatsAppExpenseConfirmationAdapter(replies).sendCaptureInstruction("9198", WebExpenseCaptureService.PORTAL_UPDATE);
        verify(replies).sendTextReply("9198", WebExpenseCaptureService.PORTAL_UPDATE);
        verifyNoMoreInteractions(replies);
    }
}
