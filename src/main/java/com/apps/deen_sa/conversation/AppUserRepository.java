package com.apps.deen_sa.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {
    Optional<AppUserEntity> findByChannelAndExternalUserId(String channel, String externalUserId);
}
