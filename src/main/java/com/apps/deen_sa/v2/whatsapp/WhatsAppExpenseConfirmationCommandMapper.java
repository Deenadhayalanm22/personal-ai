package com.apps.deen_sa.v2.whatsapp;

import com.apps.deen_sa.v2.dto.ExpenseConfirmationCommand;
import com.apps.deen_sa.v2.dto.WhatsAppWebhookPayload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class WhatsAppExpenseConfirmationCommandMapper {
    private static final String CONFIRM = "v2:expense:confirm:";
    private static final String DISCARD = "v2:expense:discard:";

    public List<ExpenseConfirmationCommand> map(WhatsAppWebhookPayload payload) {
        if (payload == null || payload.entry() == null) {
            return List.of();
        }
        return payload.entry().stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.changes() != null)
                .flatMap(entry -> entry.changes().stream())
                .filter(Objects::nonNull)
                .filter(change -> change.value() != null && change.value().messages() != null)
                .flatMap(change -> change.value().messages().stream())
                .filter(Objects::nonNull)
                .map(this::mapMessage)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<ExpenseConfirmationCommand> mapMessage(WhatsAppWebhookPayload.Message message) {
        if (!"interactive".equals(message.type()) || message.interactive() == null) {
            return Optional.empty();
        }
        String replyId = message.interactive().replyId();
        if (replyId == null) {
            return Optional.empty();
        }
        if (replyId.startsWith(CONFIRM)) {
            return command(message.from(), replyId, CONFIRM, ExpenseConfirmationCommand.Action.CONFIRM);
        }
        if (replyId.startsWith(DISCARD)) {
            return command(message.from(), replyId, DISCARD, ExpenseConfirmationCommand.Action.DISCARD);
        }
        return Optional.empty();
    }

    private Optional<ExpenseConfirmationCommand> command(
            String userId,
            String replyId,
            String prefix,
            ExpenseConfirmationCommand.Action action
    ) {
        try {
            return Optional.of(new ExpenseConfirmationCommand(
                    userId, Long.parseLong(replyId.substring(prefix.length())), action));
        } catch (NumberFormatException invalidId) {
            return Optional.empty();
        }
    }
}
