package com.apps.deen_sa.cooking.session;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.Collection;

public interface CookingSessionRepository extends JpaRepository<CookingSessionEntity, Long> {
    Optional<CookingSessionEntity> findFirstByUserIdAndStatusInOrderByUpdatedAtDesc(
            Long userId, Collection<CookingSessionStatus> statuses);
}
