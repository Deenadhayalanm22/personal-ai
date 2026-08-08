package com.apps.deen_sa.extension.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ExtensionInstallationService {
    private final ExtensionInstallationRepository repository;
    private final ExtensionInstallationAuditRepository auditRepository;
    private final boolean enableDiscoveredByDefault;

    public ExtensionInstallationService(ExtensionInstallationRepository repository,
            ExtensionInstallationAuditRepository auditRepository,
            @Value("${extensions.enable-discovered-by-default:false}") boolean enableDiscoveredByDefault) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.enableDiscoveredByDefault = enableDiscoveredByDefault;
    }

    public boolean isEnabled(Long tenantId, String extensionId) {
        if (tenantId == null) tenantId = 1L;
        return repository.findByTenantIdAndExtensionId(tenantId, extensionId)
                .map(value -> "ENABLED".equals(value.getStatus())).orElse(enableDiscoveredByDefault);
    }

    public boolean isInstalled(Long tenantId, String extensionId) {
        return repository.findByTenantIdAndExtensionId(tenantId == null ? 1L : tenantId, extensionId).isPresent();
    }

    @Transactional
    public ExtensionInstallationEntity install(Long tenantId, String extensionId, String version,
                                                Map<String, Object> configuration) {
        ExtensionInstallationEntity value = repository.findByTenantIdAndExtensionId(tenantId, extensionId)
                .orElseGet(ExtensionInstallationEntity::new);
        value.setTenantId(tenantId); value.setExtensionId(extensionId); value.setExtensionVersion(version);
        value.setStatus("ENABLED"); value.setConfiguration(configuration == null ? Map.of() : Map.copyOf(configuration));
        value = repository.save(value);
        audit(value, "ENABLED");
        return value;
    }

    @Transactional
    public void disable(Long tenantId, String extensionId, String version) {
        ExtensionInstallationEntity value = repository.findByTenantIdAndExtensionId(tenantId, extensionId)
                .orElseGet(ExtensionInstallationEntity::new);
        value.setTenantId(tenantId); value.setExtensionId(extensionId); value.setExtensionVersion(version);
        value.setStatus("DISABLED");
        if (value.getConfiguration() == null) value.setConfiguration(Map.of());
        repository.save(value);
        audit(value, "DISABLED");
    }

    private void audit(ExtensionInstallationEntity installation, String action) {
        ExtensionInstallationAuditEntity audit = new ExtensionInstallationAuditEntity();
        audit.setTenantId(installation.getTenantId()); audit.setExtensionId(installation.getExtensionId());
        audit.setExtensionVersion(installation.getExtensionVersion()); audit.setAction(action);
        audit.setConfiguration(installation.getConfiguration()); audit.setOccurredAt(java.time.Instant.now());
        auditRepository.save(audit);
    }
}
