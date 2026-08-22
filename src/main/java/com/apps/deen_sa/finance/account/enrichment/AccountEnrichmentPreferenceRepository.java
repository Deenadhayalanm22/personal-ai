package com.apps.deen_sa.finance.account.enrichment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountEnrichmentPreferenceRepository extends JpaRepository<AccountEnrichmentPreferenceEntity, Long> {
    Optional<AccountEnrichmentPreferenceEntity> findByAccountIdAndFieldName(Long accountId, String fieldName);
    List<AccountEnrichmentPreferenceEntity> findByAccountId(Long accountId);
}
