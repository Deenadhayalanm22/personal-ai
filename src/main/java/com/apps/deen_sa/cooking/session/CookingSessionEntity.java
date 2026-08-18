package com.apps.deen_sa.cooking.session;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cooking_session")
@Getter @Setter
public class CookingSessionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "recipe_id", nullable = false, length = 100)
    private String recipeId;
    @Column(name = "recipe_version", nullable = false)
    private int recipeVersion;
    @Column(name = "rice_grams", nullable = false, precision = 10, scale = 1)
    private BigDecimal riceGrams;
    @Column(name = "chicken_grams", nullable = false, precision = 10, scale = 1)
    private BigDecimal chickenGrams;
    @Column(name = "current_step", nullable = false)
    private int currentStep;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private CookingSessionStatus status;
    @Column(name = "adjustment_notes", columnDefinition = "TEXT")
    private String adjustmentNotes;
    @Column(name = "rice_type", length = 40)
    private String riceType;
    @Column(length = 40)
    private String equipment;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
