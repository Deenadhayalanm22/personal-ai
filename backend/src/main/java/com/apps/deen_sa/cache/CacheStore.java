package com.apps.deen_sa.cache;

import java.time.Duration;

/**
 * Storage boundary for disposable JSON responses. A future Redis adapter implements this
 * contract without changing controllers, cache identities, serialization or invalidation callers.
 * Generations must be unique; responses from an old generation must never be returned in a new one.
 */
public interface CacheStore {
    String generation();
    String get(String generation, String key);
    void put(String generation, String key, String json, Duration ttl);
    void evict(String generation, String key);
    void invalidate();
}
