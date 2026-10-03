package com.apps.deen_sa.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Backend-independent response wrapper: memory today; a Redis CacheStore can be plugged in later. */
@Component
public class PortalReadCache {
    private static final Logger log = LoggerFactory.getLogger(PortalReadCache.class);
    private final CacheStore store;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final java.time.Duration ttl;
    private final int maxEntryBytes;
    private final Object[] locks = new Object[64];
    private volatile long retryAfterNanos;
    private volatile boolean recoveryRequired;

    public PortalReadCache(CacheStore store, ObjectMapper mapper,
            @Value("${app.read-cache.enabled:true}") boolean enabled,
            @Value("${app.read-cache.ttl:60s}") String ttl,
            @Value("${app.read-cache.max-entry-bytes:1048576}") int maxEntryBytes) {
        this.store = store; this.mapper = mapper; this.enabled = enabled;
        this.ttl = DurationStyle.detectAndParse(ttl); this.maxEntryBytes = maxEntryBytes;
        if (this.ttl.isZero() || this.ttl.isNegative() || maxEntryBytes <= 0)
            throw new IllegalArgumentException("Read cache requires positive TTL and entry size");
        java.util.Arrays.setAll(locks, i -> new Object());
    }

    @FunctionalInterface
    public interface Loader { Object load() throws Throwable; }

    public Object get(String identity, JavaType type, Loader loader) throws Throwable {
        if (!available()) return loader.load();
        // Bounded local locks collapse identical concurrent page loads without retaining user keys.
        synchronized (locks[Math.floorMod(identity.hashCode(), locks.length)]) {
            String generation;
            String key;
            try {
                generation = generation();
                key = identity;
                String json = store.get(generation, key);
                if (json != null) {
                    try { return mapper.readValue(json, type); }
                    catch (Exception corruptEntry) { store.evict(generation, key); }
                }
            } catch (RuntimeException unavailable) {
                failed();
                return loader.load();
            }
            Object value = loader.load(); // Errors are never cached or retried by the cache.
            if (value != null) {
                try {
                    String json = mapper.writeValueAsString(value);
                    // A concurrent committed write makes this generation unreachable. Do not publish
                    // a response computed before that write into the new generation.
                    if (json.getBytes(StandardCharsets.UTF_8).length <= maxEntryBytes
                            && generation.equals(generation())) store.put(generation, key, json, ttl);
                } catch (RuntimeException unavailable) { failed(); }
                catch (Exception unserializable) { log.debug("Skipping unserializable portal response"); }
            }
            return value;
        }
    }

    public void invalidate() {
        if (!enabled) return;
        if (!available()) { recoveryRequired = true; return; }
        try { store.invalidate(); }
        catch (RuntimeException unavailable) { failed(); }
    }

    private synchronized String generation() {
        if (recoveryRequired) {
            store.invalidate();
            recoveryRequired = false;
        }
        return store.generation();
    }

    private boolean available() { return enabled && System.nanoTime() >= retryAfterNanos; }

    private synchronized void failed() {
        if (System.nanoTime() >= retryAfterNanos)
            log.warn("Portal read cache unavailable; serving uncached reads and retrying in 5 seconds");
        recoveryRequired = true;
        retryAfterNanos = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
    }
}
