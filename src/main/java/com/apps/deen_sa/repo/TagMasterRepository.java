package com.apps.deen_sa.repo;

import com.apps.deen_sa.entity.TagMasterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagMasterRepository
        extends JpaRepository<TagMasterEntity, Long> {

    @Query("""
        SELECT t.canonicalTag
        FROM TagMasterEntity t
        WHERE t.status = 'ACTIVE'
    """)
    List<String> findCanonicalTags();

    Optional<TagMasterEntity> findByCanonicalTagIgnoreCase(String canonicalTag);
}
