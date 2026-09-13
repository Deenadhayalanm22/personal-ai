package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.UserLoanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserLoanRepository extends JpaRepository<UserLoanEntity, Long> {
    List<UserLoanEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<UserLoanEntity> findByIdAndUserId(Long id, Long userId);
}
