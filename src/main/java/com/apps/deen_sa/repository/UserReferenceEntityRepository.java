package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.UserReferenceEntity;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT reference FROM UserReferenceEntity reference WHERE reference.id IN :ids ORDER BY reference.id")
    List<UserReferenceEntity> findAllByIdForUpdate(@Param("ids") List<Long> ids);
}
