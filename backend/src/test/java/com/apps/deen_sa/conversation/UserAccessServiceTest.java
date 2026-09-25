package com.apps.deen_sa.conversation;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.service.UserAccessService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserAccessServiceTest {
    private final AppUserRepository repository = mock(AppUserRepository.class);
    private final UserAccessService service = new UserAccessService(repository);

    @Test
    void defaultsToDisabledWhenNoAccessExists() {
        assertThat(service.hasAnyEnabledFeature("whatsapp", "+91 98765-43210")).isFalse();
    }

    @Test
    void identifiesAnEnabledSuperAdmin() {
        AppUserEntity access = access("SUPER_ADMIN", true);
        when(repository.findByChannelAndExternalUserId("WHATSAPP", "919876543210"))
                .thenReturn(Optional.of(access));
        assertThat(service.isSuperAdmin("whatsapp", "+91 98765-43210")).isTrue();
    }

    @Test
    void deniesPortalAccessAndAdminCommandsForDisabledUser() {
        when(repository.findByChannelAndExternalUserId("WHATSAPP", "919876543210"))
                .thenReturn(Optional.of(access("SUPER_ADMIN", false)));

        assertThat(service.hasAnyEnabledFeature("WHATSAPP", "919876543210")).isFalse();
        assertThat(service.isSuperAdmin("WHATSAPP", "919876543210")).isFalse();
    }

    @Test
    void grantsNormalUserAccessWithoutDowngradingExistingRole() {
        when(repository.findByChannelAndExternalUserId("WHATSAPP", "919876543211"))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AppUserEntity saved = service.grantWhatsAppAccess("+91 98765-43211");

        assertThat(saved.getExternalUserId()).isEqualTo("919876543211");
        assertThat(saved.getRole()).isEqualTo("USER");
        assertThat(saved.isPortalEnabled()).isTrue();
    }

    @Test
    void refusesToRevokeSuperAdmin() {
        when(repository.findByChannelAndExternalUserId("WHATSAPP", "919876543210"))
                .thenReturn(Optional.of(access("SUPER_ADMIN", true)));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.revokeWhatsAppAccess("919876543210"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AppUserEntity access(String role, boolean enabled) {
        AppUserEntity value = new AppUserEntity();
        value.setChannel("WHATSAPP"); value.setExternalUserId("919876543210");
        value.setRole(role); value.setPortalEnabled(enabled);
        return value;
    }
}
