package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.UserInvestmentEntity;
import com.apps.deen_sa.domain.InvestmentAssetType;
import com.apps.deen_sa.domain.InvestmentSipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserInvestmentRepository extends JpaRepository<UserInvestmentEntity, Long> {
    List<UserInvestmentEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<UserInvestmentEntity> findByIdAndUserId(Long id, Long userId);
    List<UserInvestmentEntity> findByAssetTypeAndSipStatus(InvestmentAssetType assetType, InvestmentSipStatus sipStatus);
}
