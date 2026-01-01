package com.apps.deen_sa.core.state.cache;

import com.apps.deen_sa.core.state.StateContainerEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryStateContainerCache implements StateContainerCache {

    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    // Simple TTL to avoid eternal lies
    private static final long TTL_MILLIS = 5 * 60 * 1000; // 5 minutes

    @Override
    public List<StateContainerEntity> getActiveContainers(Long ownerId) {
        CacheEntry entry = cache.get(ownerId);

        if (entry == null || entry.isExpired()) {
            cache.remove(ownerId);
            return null;
        }
        return entry.containers;
    }

    @Override
    public void putActiveContainers(Long ownerId, List<StateContainerEntity> containers) {
        cache.put(ownerId, new CacheEntry(containers));
    }

    @Override
    public void evict(Long ownerId) {
        cache.remove(ownerId);
    }

    @Override
    public void evictAll() {
        cache.clear();
    }

    private static class CacheEntry {
        private final List<StateContainerEntity> containers;
        private final long createdAt = System.currentTimeMillis();

        CacheEntry(List<StateContainerEntity> containers) {
            this.containers = containers;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > TTL_MILLIS;
        }
    }
}
