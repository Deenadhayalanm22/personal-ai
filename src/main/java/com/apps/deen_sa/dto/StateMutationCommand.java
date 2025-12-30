package com.apps.deen_sa.dto;

import com.apps.deen_sa.core.mutation.MutationTypeEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class StateMutationCommand {
    private final BigDecimal amount;
    private final MutationTypeEnum type; // DEBIT | CREDIT
    private final String reason;        // EXPENSE | REVERSAL | EDIT
    private final Long referenceTxId;   // original transaction
    private final Instant occurredAt;
}
