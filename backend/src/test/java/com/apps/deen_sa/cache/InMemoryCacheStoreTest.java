package com.apps.deen_sa.cache;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryCacheStoreTest {
    @Test void springCreatesDefaultCacheWithNoExternalService() throws Throwable {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(com.fasterxml.jackson.databind.ObjectMapper.class, () -> new com.fasterxml.jackson.databind.ObjectMapper());
            context.register(InMemoryCacheStore.class, PortalReadCache.class);
            context.refresh();
            assertInstanceOf(InMemoryCacheStore.class, context.getBean(CacheStore.class));
            var cache = context.getBean(PortalReadCache.class);
            var type = context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class).constructType(String.class);
            assertEquals("first", cache.get("user", type, () -> "first"));
            assertEquals("first", cache.get("user", type, () -> "second"));
        }
    }

    @Test void expiresAtDeadlineWithoutExtendingTtlOnReads() {
        AtomicLong time = new AtomicLong();
        var store = new InMemoryCacheStore(10, 1024, time::get);
        String generation = store.generation();
        store.put(generation, "key", "value", Duration.ofSeconds(10));
        time.set(Duration.ofSeconds(9).toNanos()); assertEquals("value", store.get(generation, "key"));
        time.set(Duration.ofSeconds(10).toNanos()); assertNull(store.get(generation, "key"));
    }

    @Test void boundsEntryCountAndEvictsLeastRecentlyUsed() {
        var store = new InMemoryCacheStore(2, 1024);
        String g = store.generation();
        store.put(g, "a", "A", Duration.ofMinutes(1));
        store.put(g, "b", "B", Duration.ofMinutes(1));
        assertEquals("A", store.get(g, "a"));
        store.put(g, "c", "C", Duration.ofMinutes(1));
        assertNull(store.get(g, "b")); assertEquals("A", store.get(g, "a")); assertEquals("C", store.get(g, "c"));
    }

    @Test void boundsUtf8BytesAndRejectsOversizedValues() {
        var store = new InMemoryCacheStore(10, 8);
        String g = store.generation();
        store.put(g, "a", "AAAA", Duration.ofMinutes(1));
        store.put(g, "b", "BBBB", Duration.ofMinutes(1));
        assertNull(store.get(g, "a")); assertEquals("BBBB", store.get(g, "b"));
        store.put(g, "c", "too big for cache", Duration.ofMinutes(1)); assertNull(store.get(g, "c"));
        store.put(g, "x", "₹₹", Duration.ofMinutes(1)); assertNull(store.get(g, "b")); assertEquals("₹₹", store.get(g, "x"));
    }

    @Test void invalidationRejectsOldGenerationAndReleasesCapacity() {
        var store = new InMemoryCacheStore(1, 8);
        String old = store.generation();
        store.put(old, "a", "AAAA", Duration.ofMinutes(1)); store.invalidate();
        assertNotEquals(old, store.generation());
        store.put(old, "a", "late", Duration.ofMinutes(1)); assertNull(store.get(old, "a"));
        String fresh = store.generation(); store.put(fresh, "b", "BBBB", Duration.ofMinutes(1));
        assertNull(store.get(fresh, "a")); assertEquals("BBBB", store.get(fresh, "b"));
    }

    @Test void restartStartsWithAnEmptyIndependentCache() {
        var first = new InMemoryCacheStore(10, 1024);
        first.put(first.generation(), "a", "A", Duration.ofMinutes(1));
        var restarted = new InMemoryCacheStore(10, 1024);
        assertNull(restarted.get(restarted.generation(), "a"));
    }
}
