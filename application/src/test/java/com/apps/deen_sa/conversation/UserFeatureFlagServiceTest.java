package com.apps.deen_sa.conversation;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserFeatureFlagServiceTest {

    private final UserFeatureFlagRepository repository = mock(UserFeatureFlagRepository.class);
    private final UserFeatureFlagService service = new UserFeatureFlagService(repository);

    @Test
    void defaultsToDisabledWhenNoFlagExists() {
        when(repository.findByChannelAndExternalUserIdAndFeatureKey("WHATSAPP", "919876543210", "EXPENSE"))
                .thenReturn(Optional.empty());

        assertThat(service.isEnabled("whatsapp", "+91 98765-43210", "expense")).isFalse();

        verify(repository).findByChannelAndExternalUserIdAndFeatureKey("WHATSAPP", "919876543210", "EXPENSE");
    }

    @Test
    void allowsAnExplicitlyEnabledFlag() {
        UserFeatureFlagEntity flag = new UserFeatureFlagEntity();
        flag.setEnabled(true);
        when(repository.findByChannelAndExternalUserIdAndFeatureKey("WHATSAPP", "919876543210", "EXPENSE"))
                .thenReturn(Optional.of(flag));

        assertThat(service.isEnabled("WHATSAPP", "919876543210", UserFeatureFlagService.EXPENSE)).isTrue();
    }

    @Test
    void allowsIngressWhenTheMobileHasAnyEnabledFeature() {
        when(repository.existsByChannelAndExternalUserIdAndEnabledTrue("WHATSAPP", "919876543210"))
                .thenReturn(true);

        assertThat(service.hasAnyEnabledFeature("whatsapp", "+91 98765-43210")).isTrue();

        verify(repository).existsByChannelAndExternalUserIdAndEnabledTrue("WHATSAPP", "919876543210");
    }
}
