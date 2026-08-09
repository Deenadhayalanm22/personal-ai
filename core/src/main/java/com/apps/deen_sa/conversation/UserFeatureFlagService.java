package com.apps.deen_sa.conversation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserFeatureFlagService {
    public static final String EXPENSE = "EXPENSE";
    public static final String SAREE_JOB_WORK = "SAREE_JOB_WORK";

    private final UserFeatureFlagRepository repository;

    public boolean hasAnyEnabledFeature(String channel, String externalUserId) {
        if (channel == null || externalUserId == null) return false;
        return repository.existsByChannelAndExternalUserIdAndEnabledTrue(
                normalizeChannel(channel), normalizeExternalUserId(channel, externalUserId));
    }

    public boolean isEnabled(String channel, String externalUserId, String featureKey) {
        if (channel == null || externalUserId == null || featureKey == null) return false;

        return repository.findByChannelAndExternalUserIdAndFeatureKey(
                        normalizeChannel(channel), normalizeExternalUserId(channel, externalUserId), normalizeFeatureKey(featureKey))
                .map(UserFeatureFlagEntity::isEnabled)
                .orElse(false);
    }

    private String normalizeChannel(String channel) {
        return channel.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeFeatureKey(String featureKey) {
        return featureKey.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeExternalUserId(String channel, String externalUserId) {
        if ("WHATSAPP".equals(normalizeChannel(channel))) {
            return externalUserId.replaceAll("[^0-9]", "");
        }
        return externalUserId.trim();
    }
}
