package com.apps.deen_sa.conversation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserFeatureFlagService {
    public static final String USER = "USER";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final UserFeatureFlagRepository repository;

    public boolean hasAnyEnabledFeature(String channel, String externalUserId) {
        if (channel == null || externalUserId == null) return false;
        return repository.existsByChannelAndExternalUserIdAndEnabledTrue(
                normalizeChannel(channel), normalizeExternalUserId(channel, externalUserId));
    }

    public boolean isSuperAdmin(String channel, String externalUserId) {
        return find(channel, externalUserId)
                .filter(UserFeatureFlagEntity::isEnabled)
                .map(UserFeatureFlagEntity::getRole)
                .map(SUPER_ADMIN::equals)
                .orElse(false);
    }

    public UserFeatureFlagEntity grantWhatsAppAccess(String externalUserId) {
        String normalized = normalizeExternalUserId("WHATSAPP", externalUserId);
        if (normalized.isBlank()) throw new IllegalArgumentException("A WhatsApp number is required.");
        UserFeatureFlagEntity access = repository.findByChannelAndExternalUserId("WHATSAPP", normalized)
                .orElseGet(UserFeatureFlagEntity::new);
        access.setChannel("WHATSAPP");
        access.setExternalUserId(normalized);
        if (access.getRole() == null || access.getRole().isBlank()) access.setRole(USER);
        access.setEnabled(true);
        return repository.save(access);
    }

    public boolean revokeWhatsAppAccess(String externalUserId) {
        return repository.findByChannelAndExternalUserId(
                        "WHATSAPP", normalizeExternalUserId("WHATSAPP", externalUserId))
                .map(access -> {
                    if (SUPER_ADMIN.equals(access.getRole()))
                        throw new IllegalArgumentException("Super-admin access cannot be removed through WhatsApp.");
                    access.setEnabled(false);
                    repository.save(access);
                    return true;
                }).orElse(false);
    }

    private Optional<UserFeatureFlagEntity> find(String channel, String externalUserId) {
        if (channel == null || externalUserId == null) return Optional.empty();
        return repository.findByChannelAndExternalUserId(
                normalizeChannel(channel), normalizeExternalUserId(channel, externalUserId));
    }

    private String normalizeChannel(String channel) {
        return channel.trim().toUpperCase(Locale.ROOT);
    }

    public String normalizeExternalUserId(String channel, String externalUserId) {
        if ("WHATSAPP".equals(normalizeChannel(channel))) {
            return externalUserId.replaceAll("[^0-9]", "");
        }
        return externalUserId.trim();
    }
}
