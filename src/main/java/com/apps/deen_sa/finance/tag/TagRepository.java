package com.apps.deen_sa.finance.tag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TagRepository extends JpaRepository<TagEntity, Long> {
    boolean existsByUserIdAndNormalizedName(Long userId, String normalizedName);
    List<TagEntity> findAllByUserIdOrderByNameAsc(Long userId);
    List<TagEntity> findAllByUserIdAndIdIn(Long userId, Collection<Long> ids);
}
