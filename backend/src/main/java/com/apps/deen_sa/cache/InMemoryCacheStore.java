package com.apps.deen_sa.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Bounded, process-local LRU storage; no network, external service or persistence required. */
@Component
public class InMemoryCacheStore implements CacheStore {
    private final int maxEntries;
    private final long maxBytes;
    private final LongSupplier now;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private String generation = UUID.randomUUID().toString();
    private long bytes;

    @org.springframework.beans.factory.annotation.Autowired
    public InMemoryCacheStore(@Value("${app.read-cache.max-entries:2048}") int maxEntries,
                              @Value("${app.read-cache.max-bytes:33554432}") long maxBytes) {
        this(maxEntries, maxBytes, System::nanoTime);
    }

    InMemoryCacheStore(int maxEntries, long maxBytes, LongSupplier now) {
        if (maxEntries <= 0 || maxBytes <= 0) throw new IllegalArgumentException("Memory cache bounds must be positive");
        this.maxEntries = maxEntries; this.maxBytes = maxBytes; this.now = now;
    }

    @Override public synchronized String generation() { return generation; }

    @Override public synchronized String get(String expectedGeneration, String key) {
        if (!generation.equals(expectedGeneration)) return null;
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (now.getAsLong() - entry.expiresAt >= 0) { remove(key); return null; }
        return entry.json;
    }

    @Override public synchronized void put(String expectedGeneration, String key, String json, Duration ttl) {
        if (!generation.equals(expectedGeneration)) return;
        if (ttl.isNegative() || ttl.isZero()) return;
        long size = key.getBytes(StandardCharsets.UTF_8).length + json.getBytes(StandardCharsets.UTF_8).length;
        if (size > maxBytes) return;
        remove(key);
        // Reclaim expired entries before evicting valid least-recently-used responses.
        long instant = now.getAsLong();
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (instant - entry.expiresAt >= 0) { bytes -= entry.size; iterator.remove(); }
        }
        while (entries.size() >= maxEntries || bytes + size > maxBytes) {
            String oldest = entries.keySet().iterator().next();
            remove(oldest);
        }
        entries.put(key, new Entry(json, instant + ttl.toNanos(), size));
        bytes += size;
    }

    @Override public synchronized void evict(String expectedGeneration, String key) {
        if (generation.equals(expectedGeneration)) remove(key);
    }

    @Override public synchronized void invalidate() {
        generation = UUID.randomUUID().toString();
        entries.clear(); bytes = 0;
    }

    private void remove(String key) {
        Entry removed = entries.remove(key);
        if (removed != null) bytes -= removed.size;
    }

    private record Entry(String json, long expiresAt, long size) { }
}
