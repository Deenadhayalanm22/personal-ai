package com.apps.deen_sa.v2.repository;

import com.apps.deen_sa.v2.entity.UserReferenceAliasEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserReferenceAliasRepository extends JpaRepository<UserReferenceAliasEntity, Long> {
    Optional<UserReferenceAliasEntity> findByReferenceEntityIdAndAliasTextIgnoreCase(
            Long referenceEntityId, String aliasText);

    List<UserReferenceAliasEntity> findByReferenceEntityId(Long referenceEntityId);
}
