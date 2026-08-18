package com.apps.deen_sa.conversation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WhatsAppAccessCommandServiceTest {
    private final UserFeatureFlagService access = mock(UserFeatureFlagService.class);
    private final WhatsAppAccessCommandService commands = new WhatsAppAccessCommandService(access);

    @Test
    void superAdminCanAddAUserFromWhatsApp() {
        when(access.isSuperAdmin("WHATSAPP", "919876543210")).thenReturn(true);
        when(access.normalizeExternalUserId("WHATSAPP", "+91 98765 43211")).thenReturn("919876543211");

        assertThat(commands.execute("919876543210", "add user +91 98765 43211"))
                .contains("Access enabled for +919876543211.");
        verify(access).grantWhatsAppAccess("919876543211");
    }

    @Test
    void normalUserCannotExecuteAccessCommands() {
        when(access.isSuperAdmin("WHATSAPP", "919876543212")).thenReturn(false);
        assertThat(commands.execute("919876543212", "remove user 919876543211")).isEmpty();
        verify(access, never()).revokeWhatsAppAccess(anyString());
    }
}
