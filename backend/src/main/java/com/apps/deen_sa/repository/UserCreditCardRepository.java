package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.UserCreditCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserCreditCardRepository extends JpaRepository<UserCreditCardEntity, Long> {
    List<UserCreditCardEntity> findByUserIdAndActiveTrueOrderByCreatedAtDesc(Long userId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from UserCreditCardEntity c where c.id = :id and c.user.id = :userId")
    Optional<UserCreditCardEntity> findOwnedForUpdate(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("userId") Long userId);
    Optional<UserCreditCardEntity> findByIdAndUserId(Long id, Long userId);
}
