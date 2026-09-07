package com.apps.deen_sa.service;

import com.apps.deen_sa.conversation.UserFeatureFlagService;
import com.apps.deen_sa.conversation.WhatsAppReplySender;
import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.domain.MessageSource;
import com.apps.deen_sa.dto.InboundMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

@Service
public class WhatsAppAggregateBackfillCommandHandler {
    private static final Set<String> COMMANDS = Set.of(
            "/aggregate", "/aggregate-expenses", "/backfill-aggregates", "aggregate expenses");

    private final UserFeatureFlagService featureFlags;
    private final ExpenseDailyAggregationService aggregationService;
    private final WhatsAppReplySender replySender;
    private final Clock clock;
    private final ZoneId aggregationZone;

    public WhatsAppAggregateBackfillCommandHandler(
            UserFeatureFlagService featureFlags,
            ExpenseDailyAggregationService aggregationService,
            WhatsAppReplySender replySender,
            Clock clock,
            @Value("${app.aggregation.time-zone:Asia/Kolkata}") String aggregationTimeZone
    ) {
        this.featureFlags = featureFlags;
        this.aggregationService = aggregationService;
        this.replySender = replySender;
        this.clock = clock;
        this.aggregationZone = ZoneId.of(aggregationTimeZone);
    }

    public boolean handleIfSupported(InboundMessage message) {
        if (message.source() != MessageSource.WHATSAPP || message.inputType() != InputType.TEXT
                || message.rawContent() == null
                || !COMMANDS.contains(message.rawContent().trim().toLowerCase(Locale.ROOT))) {
            return false;
        }

        if (!featureFlags.isSuperAdmin("WHATSAPP", message.externalUserId())) {
            replySender.sendTextReply(message.externalUserId(),
                    "This aggregation command is restricted to the super admin.");
            return true;
        }

        LocalDate today = LocalDate.now(clock.withZone(aggregationZone));
        ExpenseDailyAggregationService.BackfillResult result =
                aggregationService.rebuildMissingBefore(today);
        String response = result.rebuiltDates().isEmpty()
                ? "Expense aggregates are already up to date."
                : "Expense aggregate backfill completed: %d date(s), %d aggregate row(s)."
                        .formatted(result.rebuiltDates().size(), result.aggregateRows());
        replySender.sendTextReply(message.externalUserId(), response);
        return true;
    }
}
