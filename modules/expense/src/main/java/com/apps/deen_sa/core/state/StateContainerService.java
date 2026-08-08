package com.apps.deen_sa.finance.legacy.state;

import com.apps.deen_sa.finance.legacy.state.cache.StateContainerCache;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.Instant;

@Service
public class StateContainerService {

    private final StateContainerRepository repository;
    private final StateContainerCache cache;

    public StateContainerService(StateContainerRepository repository,
                                 StateContainerCache cache) {
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
        // Evict cache to ensure next read gets fresh data
        cache.evict(entity.getOwnerId());
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

    public StateContainerEntity createProvisional(Long ownerId, String containerType) {
        StateContainerEntity container = new StateContainerEntity();
        container.setOwnerType("USER");
        container.setOwnerId(ownerId);
        container.setContainerType(containerType);
        container.setName(defaultName(containerType));
        container.setStatus("ACTIVE");
        container.setCurrency("INR");
        container.setOpenedAt(Instant.now());
        StateContainerEntity saved = repository.save(container);
        cache.evict(ownerId);
        return saved;
    }

    private String defaultName(String type) {
        return switch (type) {
            case "BANK_ACCOUNT" -> "My bank account";
            case "CREDIT_CARD" -> "My credit card";
            case "WALLET" -> "My wallet";
            case "CASH" -> "Cash";
            default -> "My " + type.toLowerCase().replace('_', ' ');
        };
    }
}
