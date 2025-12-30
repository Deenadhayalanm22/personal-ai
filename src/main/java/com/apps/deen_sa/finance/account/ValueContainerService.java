package com.apps.deen_sa.finance.account;

import com.apps.deen_sa.finance.account.ValueContainerCache;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.state.StateContainerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ValueContainerService {

    private final StateContainerRepository repository;
    private final ValueContainerCache cache;

    public ValueContainerService(StateContainerRepository repository,
                                 ValueContainerCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    public StateContainerEntity findValueContainerById (Long valueId) {
        return repository.findById(valueId)
                .orElseThrow(() ->
                        new IllegalStateException("Source container not found"));
    }

    public void UpdateValueContainer (StateContainerEntity entity) {
        repository.save(entity);
    }

    public List<StateContainerEntity> getActiveContainers(Long ownerId) {

        // 1️⃣ Try cache
        List<StateContainerEntity> cached = cache.getActiveContainers(ownerId);
        if (cached != null) {
            return cached;
        }

        // 2️⃣ Hit DB
        List<StateContainerEntity> containers =
                repository.findActiveByOwnerId(ownerId);

        // 3️⃣ Populate cache
        cache.putActiveContainers(ownerId, containers);

        return containers;
    }

    // Call this after ANY container update
    public void evictCache(Long ownerId) {
        cache.evict(ownerId);
    }
}
