package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserService {
    private final AppUserRepository repository;

    public AppUserEntity resolve(String channel, String externalUserId) {
        return repository.findByChannelAndExternalUserId(channel, externalUserId)
                .orElseGet(() -> createSafely(channel, externalUserId));
    }

    private AppUserEntity createSafely(String channel, String externalUserId) {
        AppUserEntity user = new AppUserEntity();
        user.setChannel(channel);
        user.setExternalUserId(externalUserId);
        try {
            return repository.saveAndFlush(user);
        } catch (DataIntegrityViolationException race) {
            return repository.findByChannelAndExternalUserId(channel, externalUserId)
                    .orElseThrow(() -> race);
        }
    }
}
