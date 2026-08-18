package com.apps.deen_sa.conversation;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserFeatureFlagServiceTest {
    private final UserFeatureFlagRepository repository = mock(UserFeatureFlagRepository.class);
    private final UserFeatureFlagService service = new UserFeatureFlagService(repository);

    @Test
    void defaultsToDisabledWhenNoAccessExists() {
        when(repository.existsByChannelAndExternalUserIdAndEnabledTrue("WHATSAPP", "919876543210"))
                .thenReturn(false);
        assertThat(service.hasAnyEnabledFeature("whatsapp", "+91 98765-43210")).isFalse();
    }

    @Test
    void identifiesAnEnabledSuperAdmin() {
        UserFeatureFlagEntity access = access("SUPER_ADMIN", true);
        when(repository.findByChannelAndExternalUserId("WHATSAPP", "919876543210"))
                .thenReturn(Optional.of(access));
        assertThat(service.isSuperAdmin("whatsapp", "+91 98765-43210")).isTrue();
    }

    @Test
    void grantsNormalUserAccessWithoutDowngradingExistingRole() {
        when(repository.findByChannelAndExternalUserId("WHATSAPP", "919876543211"))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserFeatureFlagEntity saved = service.grantWhatsAppAccess("+91 98765-43211");

        assertThat(saved.getExternalUserId()).isEqualTo("919876543211");
        assertThat(saved.getRole()).isEqualTo("USER");
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void refusesToRevokeSuperAdmin() {
        when(repository.findByChannelAndExternalUserId("WHATSAPP", "919876543210"))
                .thenReturn(Optional.of(access("SUPER_ADMIN", true)));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.revokeWhatsAppAccess("919876543210"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UserFeatureFlagEntity access(String role, boolean enabled) {
        UserFeatureFlagEntity value = new UserFeatureFlagEntity();
        value.setChannel("WHATSAPP"); value.setExternalUserId("919876543210");
        value.setRole(role); value.setEnabled(enabled);
        return value;
    }
}
