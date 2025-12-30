package com.apps.deen_sa.core.mutation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "value_adjustments")
public class StateMutationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long transactionId;

    private Long containerId;

    @Enumerated(EnumType.STRING)
    private MutationTypeEnum adjustmentType; // DEBIT / CREDIT

    private BigDecimal amount;

    private String reason; // EXPENSE, REVERSAL, EDIT

    private Instant occurredAt;

    private Instant createdAt;
}
