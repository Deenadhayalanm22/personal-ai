package com.apps.deen_sa.repository;
import com.apps.deen_sa.entity.MoneyStoryEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
public interface MoneyStoryEvidenceRepository extends JpaRepository<MoneyStoryEvidenceEntity, MoneyStoryEvidenceEntity.MoneyStoryEvidenceId> {
    List<MoneyStoryEvidenceEntity> findByStoryIdOrderByIdOrdinal(UUID storyId);
}
