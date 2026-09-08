package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.WebSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface WebSessionRepository extends JpaRepository<WebSessionEntity, Long> {
    Optional<WebSessionEntity> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(String tokenHash, Instant now);
}
