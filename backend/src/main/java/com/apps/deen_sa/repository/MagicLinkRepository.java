package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.MagicLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface MagicLinkRepository extends JpaRepository<MagicLinkEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MagicLinkEntity> findByTokenHash(String tokenHash);
}
