package com.apps.deen_sa.core.state;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "state_container")
public class StateContainerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // -------------------------
    // Ownership
    // -------------------------

    @Column(nullable = false, length = 30)
    private String ownerType;   // USER | BUSINESS | EMPLOYEE | CUSTOMER | VENDOR

    @Column(nullable = false)
    private Long ownerId;

    // -------------------------
    // Classification
    // -------------------------

    @Column(nullable = false, length = 30)
    private String containerType;   // CASH | BANK | CREDIT | INVENTORY | PAYABLE | RECEIVABLE

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String status;          // ACTIVE | CLOSED | SUSPENDED

    // -------------------------
    // Quantitative State
    // -------------------------

    @Column(length = 10)
    private String currency;         // INR, USD, null for quantity-based

    @Column(precision = 19, scale = 4)
    private BigDecimal currentValue;

    @Column(precision = 19, scale = 4)
    private BigDecimal availableValue;

    @Column(length = 20)
    private String unit;             // INR, kg, pcs, litres

    // -------------------------
    // Limits & Rules
    // -------------------------

    @Column(precision = 19, scale = 4)
    private BigDecimal capacityLimit;

    @Column(precision = 19, scale = 4)
    private BigDecimal minThreshold;

    private Integer priorityOrder;

    // -------------------------
    // Lifecycle
    // -------------------------

    private Instant openedAt;
    private Instant closedAt;
    private Instant lastActivityAt;

    // -------------------------
    // External References
    // -------------------------

    @Column(length = 30)
    private String externalRefType;   // BANK | WALLET | INTERNAL | SUPPLIER

    private String externalRefId;     // masked / safe reference

    // -------------------------
    // Flexible Extensions
    // -------------------------

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    // -------------------------
    // Audit
    // -------------------------

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Boolean overLimit = false;   // true if usage exceeds capacity

    @Column(precision = 19, scale = 4)
    private BigDecimal overLimitAmount; // how much exceeded
}
