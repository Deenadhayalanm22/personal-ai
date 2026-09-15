package com.apps.deen_sa.dto;

import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.domain.MessageSource;

import java.util.Objects;

public record InboundMessage(
        String externalUserId,
        String sourceMessageId,
        InputType inputType,
        MessageSource source,
        String rawContent
) {
    public InboundMessage {
        Objects.requireNonNull(externalUserId, "externalUserId is required");
        Objects.requireNonNull(sourceMessageId, "sourceMessageId is required");
        Objects.requireNonNull(inputType, "inputType is required");
        Objects.requireNonNull(source, "source is required");
    }
}
