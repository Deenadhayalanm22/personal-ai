package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserAccessService {
    public static final String USER = "USER";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final AppUserRepository repository;

    public boolean hasAnyEnabledFeature(String channel, String externalUserId) {
        if (channel == null || externalUserId == null) return false;
        return find(channel, externalUserId).map(AppUserEntity::isPortalEnabled).orElse(false);
    }

    public boolean isSuperAdmin(String channel, String externalUserId) {
        return find(channel, externalUserId)
                .filter(AppUserEntity::isPortalEnabled)
                .map(AppUserEntity::getRole)
                .map(SUPER_ADMIN::equals)
                .orElse(false);
    }

    public AppUserEntity grantWhatsAppAccess(String externalUserId) {
        String normalized = normalizeExternalUserId("WHATSAPP", externalUserId);
        if (normalized.isBlank()) throw new IllegalArgumentException("A WhatsApp number is required.");
        AppUserEntity access = repository.findByChannelAndExternalUserId("WHATSAPP", normalized)
                .orElseGet(AppUserEntity::new);
        access.setChannel("WHATSAPP");
        access.setExternalUserId(normalized);
        if (access.getRole() == null || access.getRole().isBlank()) access.setRole(USER);
        access.setPortalEnabled(true);
        return repository.save(access);
    }

    public boolean revokeWhatsAppAccess(String externalUserId) {
        return repository.findByChannelAndExternalUserId(
                        "WHATSAPP", normalizeExternalUserId("WHATSAPP", externalUserId))
                .map(access -> {
                    if (SUPER_ADMIN.equals(access.getRole()))
                        throw new IllegalArgumentException("Super-admin access cannot be removed through WhatsApp.");
                    access.setPortalEnabled(false);
                    repository.save(access);
                    return true;
                }).orElse(false);
    }

    private Optional<AppUserEntity> find(String channel, String externalUserId) {
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
