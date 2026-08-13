package com.apps.deen_sa.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserFeatureFlagRepository extends JpaRepository<UserFeatureFlagEntity, Long> {
    boolean existsByChannelAndExternalUserIdAndEnabledTrue(String channel, String externalUserId);

    Optional<UserFeatureFlagEntity> findByChannelAndExternalUserId(String channel, String externalUserId);
}
