package com.apps.deen_sa.v2.whatsapp;

import com.apps.deen_sa.v2.dto.ExpenseConfirmationCommand;
import com.apps.deen_sa.v2.dto.WhatsAppWebhookPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppExpenseConfirmationCommandMapperTest {
    private final WhatsAppExpenseConfirmationCommandMapper mapper =
            new WhatsAppExpenseConfirmationCommandMapper();

    @Test
    void mapsConfirmationButtonToOwnedExtractionCommand() {
        WhatsAppWebhookPayload payload = new WhatsAppWebhookPayload(List.of(
                new WhatsAppWebhookPayload.Entry(List.of(
                        new WhatsAppWebhookPayload.Change(new WhatsAppWebhookPayload.Value(List.of(
                                new WhatsAppWebhookPayload.Message(
                                        "wamid.reply", "9198", "interactive", null, null,
                                        new WhatsAppWebhookPayload.Interactive(
                                                new WhatsAppWebhookPayload.Reply(
                                                        "v2:expense:confirm:5001", "Confirm"),
                                                null))
                        )))
                ))
        ));

        assertThat(mapper.map(payload)).containsExactly(new ExpenseConfirmationCommand(
                "9198", 5001L, ExpenseConfirmationCommand.Action.CONFIRM));
    }
}
