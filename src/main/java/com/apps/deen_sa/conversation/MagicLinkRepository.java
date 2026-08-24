package com.apps.deen_sa.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MagicLinkRepository extends JpaRepository<MagicLinkEntity, Long> {
    Optional<MagicLinkEntity> findByTokenHash(String tokenHash);
}
