package com.apps.deen_sa.conversation;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import com.apps.deen_sa.extension.runtime.ExtensionCatalog;

@Service
@RequiredArgsConstructor
public class AppUserService {
    private final AppUserRepository repository;
    private final ExtensionCatalog extensions;

    public AppUserEntity resolve(String channel, String externalUserId) {
        return repository.findByChannelAndExternalUserId(channel, externalUserId)
                .orElseGet(() -> createSafely(channel, externalUserId));
    }

    private AppUserEntity createSafely(String channel, String externalUserId) {
        AppUserEntity user = new AppUserEntity();
        user.setChannel(channel);
        user.setExternalUserId(externalUserId);
        try {
            AppUserEntity created = repository.saveAndFlush(user);
            extensions.provisionNewTenant(created.getId());
            return created;
        } catch (DataIntegrityViolationException race) {
            return repository.findByChannelAndExternalUserId(channel, externalUserId)
                    .orElseThrow(() -> race);
        }
    }
}
