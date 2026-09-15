package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.MoneyStoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MoneyStoryRepository extends JpaRepository<MoneyStoryEntity, UUID> {
    List<MoneyStoryEntity> findBySnapshotIdOrderByDisplayOrderAscIdAsc(UUID snapshotId);
    List<MoneyStoryEntity> findBySnapshotIdOrderByImpactAmountDesc(UUID snapshotId);
}
