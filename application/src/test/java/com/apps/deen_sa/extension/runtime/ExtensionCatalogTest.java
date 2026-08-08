package com.apps.deen_sa.extension.runtime;

import com.apps.deen_sa.extension.api.BusinessExtension;
import com.apps.deen_sa.extension.api.ExtensionDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtensionCatalogTest {
    @Test
    void rejectsAnIncompatibleExtensionApiAtStartup() {
        BusinessExtension incompatible = new BusinessExtension() {
            @Override public ExtensionDescriptor descriptor() {
                return new ExtensionDescriptor("future-extension", "1.0.0", 2, "Future", false, Set.of(), List.of());
            }
            @Override public java.util.Collection<com.apps.deen_sa.extension.api.EventCapability> events() { return List.of(); }
        };
        assertThrows(IllegalStateException.class,
                () -> new ExtensionCatalog(List.of(incompatible), new ExtensionInstallationService(null, null, true)));
    }

    @Test
    void resolvesCapabilitiesOnlyForTheTenantWhereTheExtensionIsEnabled() {
        ExtensionInstallationService installations = installations(10L, "inventory");
        BusinessExtension inventory = extension("inventory", "INVENTORY_RECEIPT");
        ExtensionCatalog catalog = new ExtensionCatalog(List.of(inventory), installations);

        assertTrue(catalog.event(10L, "inventory_receipt").isPresent());
        assertFalse(catalog.event(20L, "INVENTORY_RECEIPT").isPresent());
        assertTrue(catalog.enabledEvents(20L).isEmpty());
    }

    @Test
    void rejectsEventTypeCollisionsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new ExtensionCatalog(
                List.of(extension("first", "SHARED_EVENT"), extension("second", "SHARED_EVENT")),
                installations(null, null)));
    }

    private BusinessExtension extension(String id, String eventType) {
        var capability = new com.apps.deen_sa.extension.api.EventCapability() {
            @Override public String eventType() { return eventType; }
            @Override public String schemaVersion() { return "1"; }
            @Override public Set<String> fields() { return Set.of("value"); }
            @Override public String extractionInstructions() { return ""; }
            @Override public com.apps.deen_sa.extension.api.CapabilityResult handle(
                    com.apps.deen_sa.extension.api.ExtensionEvent event, String rawText,
                    com.apps.deen_sa.extension.api.CapabilityContext context, boolean continuation) {
                return com.apps.deen_sa.extension.api.CapabilityResult.info("ok");
            }
        };
        return new BusinessExtension() {
            @Override public ExtensionDescriptor descriptor() {
                return new ExtensionDescriptor(id, "1.0.0", 1, id, false, Set.of(), List.of());
            }
            @Override public java.util.Collection<com.apps.deen_sa.extension.api.EventCapability> events() {
                return List.of(capability);
            }
        };
    }

    private ExtensionInstallationService installations(Long enabledTenant, String enabledExtension) {
        return new ExtensionInstallationService(null, null, false) {
            @Override public boolean isEnabled(Long tenantId, String extensionId) {
                return java.util.Objects.equals(enabledTenant, tenantId)
                        && java.util.Objects.equals(enabledExtension, extensionId);
            }
        };
    }
}
