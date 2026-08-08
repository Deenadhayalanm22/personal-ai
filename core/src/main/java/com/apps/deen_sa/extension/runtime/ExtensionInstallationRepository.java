package com.apps.deen_sa.extension.runtime;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface ExtensionInstallationRepository extends JpaRepository<ExtensionInstallationEntity, Long> {
    Optional<ExtensionInstallationEntity> findByTenantIdAndExtensionId(Long tenantId, String extensionId);
}
