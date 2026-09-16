package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.UserRecurringCommitmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRecurringCommitmentRepository extends JpaRepository<UserRecurringCommitmentEntity, Long> {
    @Query("select distinct c from UserRecurringCommitmentEntity c left join fetch c.merchant left join fetch c.linkedTransactions where c.user.id = :userId order by c.label")
    List<UserRecurringCommitmentEntity> findAllOwned(@Param("userId") Long userId);
    @Query("select distinct c from UserRecurringCommitmentEntity c left join fetch c.merchant left join fetch c.linkedTransactions where c.id = :id and c.user.id = :userId")
    Optional<UserRecurringCommitmentEntity> findOwned(@Param("id") Long id, @Param("userId") Long userId);
}
