package com.apps.deen_sa.repository;

import com.apps.deen_sa.domain.UserActionItemStatus;
import com.apps.deen_sa.domain.UserActionItemType;
import com.apps.deen_sa.entity.UserActionItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserActionItemRepository extends JpaRepository<UserActionItemEntity, Long> {
    List<UserActionItemEntity> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, UserActionItemStatus status);
    Optional<UserActionItemEntity> findByIdAndUserIdAndStatus(Long id, Long userId, UserActionItemStatus status);
    boolean existsByUserIdAndActionTypeAndReferenceTypeAndReferenceIdAndStatus(
            Long userId, UserActionItemType actionType, String referenceType, Long referenceId,
            UserActionItemStatus status);
    void deleteByUserIdAndReferenceTypeAndReferenceId(Long userId, String referenceType, Long referenceId);
}
