package com.apps.deen_sa.conversation.context;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface PendingActionContextRepository extends JpaRepository<PendingActionContextEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PendingActionContextEntity c WHERE c.userId=:userId AND c.status=com.apps.deen_sa.conversation.context.PendingActionContextStatus.ACTIVE")
    List<PendingActionContextEntity> findActiveForUpdate(@Param("userId") Long userId);

    Optional<PendingActionContextEntity> findFirstByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId, PendingActionContextStatus status, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PendingActionContextEntity c WHERE c.id=:id AND c.userId=:userId")
    Optional<PendingActionContextEntity> findOwnedForUpdate(@Param("id") String id, @Param("userId") Long userId);
}
