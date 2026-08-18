package com.apps.deen_sa.cooking.session;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CookingSetupRepository extends JpaRepository<CookingSetupEntity, Long> {
    Optional<CookingSetupEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
