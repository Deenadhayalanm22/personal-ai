package com.apps.deen_sa.extension.runtime;

import com.apps.deen_sa.extension.api.ExtensionDescriptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/extensions")
public class ExtensionManagementController {
    private final ExtensionCatalog catalog;
    private final ExtensionInstallationService installations;

    public ExtensionManagementController(ExtensionCatalog catalog, ExtensionInstallationService installations) {
        this.catalog = catalog; this.installations = installations;
    }

    @GetMapping
    public Collection<ExtensionDescriptor> enabled(@PathVariable Long tenantId) {
        return catalog.enabledDescriptors(tenantId);
    }

    @PutMapping("/{extensionId}")
    public ExtensionInstallationEntity install(@PathVariable Long tenantId, @PathVariable String extensionId,
                                               @RequestBody(required = false) Map<String, Object> configuration) {
        ExtensionDescriptor descriptor = catalog.requireDescriptor(extensionId);
        return installations.install(tenantId, descriptor.id(), descriptor.version(), configuration);
    }

    @DeleteMapping("/{extensionId}")
    public ResponseEntity<Void> disable(@PathVariable Long tenantId, @PathVariable String extensionId) {
        ExtensionDescriptor descriptor = catalog.requireDescriptor(extensionId);
        installations.disable(tenantId, extensionId, descriptor.version());
        return ResponseEntity.noContent().build();
    }
}
