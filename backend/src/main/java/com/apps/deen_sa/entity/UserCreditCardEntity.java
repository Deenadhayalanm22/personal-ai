package com.apps.deen_sa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** FIN-022 — a card billing cycle attached to the existing ACCOUNT reference used by expense capture. */
@Entity
@Table(name = "user_credit_card")
@Getter @Setter
public class UserCreditCardEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private AppUserEntity user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_reference_id", nullable = false) private UserReferenceEntity accountReference;
    @Column(name = "card_name", nullable = false, length = 120) private String cardName;
    @Column(name = "issuer_name", nullable = false, length = 120) private String issuerName;
    @Column(name = "statement_day", nullable = false) private int statementDay;
    @Column(name = "due_day", nullable = false) private int dueDay;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
}
