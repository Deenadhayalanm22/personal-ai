package com.apps.deen_sa.finance.tag;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "transaction_tag")
@IdClass(TransactionTagId.class)
@Getter
@Setter
public class TransactionTagEntity {
    @Id
    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
