package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.UserCreditCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserCreditCardRepository extends JpaRepository<UserCreditCardEntity, Long> {
    List<UserCreditCardEntity> findByUserIdAndActiveTrueOrderByCreatedAtDesc(Long userId);
    Optional<UserCreditCardEntity> findByIdAndUserId(Long id, Long userId);
}
