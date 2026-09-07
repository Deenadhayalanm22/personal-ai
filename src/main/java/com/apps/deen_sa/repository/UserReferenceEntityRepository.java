package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.UserReferenceEntity;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserReferenceEntityRepository extends JpaRepository<UserReferenceEntity, Long> {
    Optional<UserReferenceEntity> findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(
            Long userId, UserReferenceEntityType entityType, String canonicalName);

    List<UserReferenceEntity> findByUserIdAndEntityTypeAndActiveTrue(
            Long userId, UserReferenceEntityType entityType);

    List<UserReferenceEntity> findByUserIdAndEntityTypeAndActiveTrueOrderByCanonicalNameAsc(
            Long userId, UserReferenceEntityType entityType);

    List<UserReferenceEntity> findByUserIdAndActiveTrueOrderByEntityTypeAscCanonicalNameAsc(
            Long userId);

    List<UserReferenceEntity> findByUserExternalUserIdAndUserChannelAndEntityTypeAndActiveTrue(
            String externalUserId, String channel, UserReferenceEntityType entityType);

    Optional<UserReferenceEntity> findByIdAndUserIdAndEntityTypeAndActiveTrue(
            Long id, Long userId, UserReferenceEntityType entityType);
}
