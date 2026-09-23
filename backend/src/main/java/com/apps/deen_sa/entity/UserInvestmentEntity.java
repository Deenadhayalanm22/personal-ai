package com.apps.deen_sa.entity;

import com.apps.deen_sa.domain.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_investment")
@Getter @Setter
public class UserInvestmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;
    @Enumerated(EnumType.STRING) @Column(name = "asset_type", nullable = false, length = 30)
    private InvestmentAssetType assetType;
    @Column(nullable = false, length = 50) private String provider;
    @Column(name = "external_instrument_id", nullable = false, length = 100) private String externalInstrumentId;
    @Column(name = "display_name_snapshot", nullable = false) private String displayNameSnapshot;
    @Column(name = "isin_snapshot", length = 30) private String isinSnapshot;
    @Column(length = 30) private String exchange;
    @Column(name = "sip_amount", precision = 19, scale = 2) private BigDecimal sipAmount;
    @Column(name = "sip_day") private Integer sipDay;
    @Column(name = "sip_start_month") private LocalDate sipStartMonth;
    @Enumerated(EnumType.STRING) @Column(name = "sip_status", length = 20)
    private InvestmentSipStatus sipStatus;
    @Column(name = "sip_frequency", nullable = false, length = 12) private String sipFrequency = "MONTHLY";
    @Column(name = "sip_anchor_month") private LocalDate sipAnchorMonth;
    @Column(name = "current_nav_override", precision = 19, scale = 6) private BigDecimal currentNavOverride;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
}
