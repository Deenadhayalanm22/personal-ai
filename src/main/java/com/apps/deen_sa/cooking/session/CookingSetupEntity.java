package com.apps.deen_sa.cooking.session;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cooking_setup", uniqueConstraints = @UniqueConstraint(name = "uq_cooking_setup_user", columnNames = "user_id"))
@Getter @Setter
public class CookingSetupEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private CookingSetupStage stage;
    @Column(name = "chicken_grams", precision = 10, scale = 1)
    private BigDecimal chickenGrams;
    @Column(name = "rice_grams", precision = 10, scale = 1)
    private BigDecimal riceGrams;
    @Column(name = "rice_type", length = 40)
    private String riceType;
    @Column(length = 40)
    private String equipment;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
