package com.apps.deen_sa.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PortalReadCacheTest {
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    final AtomicLong time = new AtomicLong();
    CacheStore store;
    PortalReadCache cache;
    public record Entry(LocalDate date, String label) { }

    @BeforeEach void setup() {
        store = spy(new InMemoryCacheStore(2048, 33554432, time::get));
        cache = new PortalReadCache(store, mapper, true, "60s", 1048576);
    }

    @Test void roundTripsGenericListsAndReusesReads() throws Throwable {
        var type = mapper.getTypeFactory().constructCollectionType(List.class, Entry.class);
        AtomicInteger loads = new AtomicInteger();
        PortalReadCache.Loader loader = () -> { loads.incrementAndGet(); return List.of(new Entry(LocalDate.of(2026, 10, 3), "expense")); };
        assertEquals(cache.get("user-1", type, loader), cache.get("user-1", type, loader));
        assertEquals(1, loads.get());
        verify(store).put(anyString(), eq("user-1"), anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test void invalidationAndExpiryForceReload() throws Throwable {
        var type = mapper.constructType(String.class);
        assertEquals("old", cache.get("user", type, () -> "old"));
        cache.invalidate();
        assertEquals("new", cache.get("user", type, () -> "new"));
        time.addAndGet(Duration.ofSeconds(61).toNanos());
        assertEquals("expired", cache.get("user", type, () -> "expired"));
    }

    @Test void concurrentWriteCannotPublishStaleLoad() throws Throwable {
        var type = mapper.constructType(String.class);
        assertEquals("old", cache.get("user", type, () -> { cache.invalidate(); return "old"; }));
        assertEquals("fresh", cache.get("user", type, () -> "fresh"));
    }

    @Test void errorsAndNullsAreNotCached() throws Throwable {
        var type = mapper.constructType(String.class);
        assertThrows(IllegalArgumentException.class, () -> cache.get("user", type, () -> { throw new IllegalArgumentException(); }));
        assertNull(cache.get("user", type, () -> null));
        assertEquals("valid", cache.get("user", type, () -> "valid"));
    }

    @Test void corruptJsonReloads() throws Throwable {
        store.put(store.generation(), "user", "{", Duration.ofSeconds(60));
        assertEquals("fresh", cache.get("user", mapper.constructType(String.class), () -> "fresh"));
    }

    @Test void futureStoreFailureFallsBackWithoutRetryingEveryRequest() throws Throwable {
        doThrow(new IllegalStateException("offline")).when(store).generation();
        var type = mapper.constructType(String.class);
        assertEquals("fresh", cache.get("user", type, () -> "fresh"));
        assertEquals("next", cache.get("user", type, () -> "next"));
        cache.invalidate();
        verify(store, times(1)).generation();
    }

    @Test void storeWriteFailureDoesNotRetryLoader() throws Throwable {
        doThrow(new IllegalStateException("offline")).when(store).put(anyString(), anyString(), anyString(), any(Duration.class));
        AtomicInteger loads = new AtomicInteger();
        assertEquals("fresh", cache.get("user", mapper.constructType(String.class), () -> { loads.incrementAndGet(); return "fresh"; }));
        assertEquals(1, loads.get());
    }

    @Test void oversizedEntriesAndDisabledCacheAreSkipped() throws Throwable {
        cache = new PortalReadCache(store, mapper, true, "60s", 2);
        cache.get("user", mapper.constructType(String.class), () -> "too large");
        verify(store, never()).put(anyString(), anyString(), anyString(), any(Duration.class));
        clearInvocations(store);
        cache = new PortalReadCache(store, mapper, false, "60s", 2);
        assertEquals("fresh", cache.get("user", mapper.constructType(String.class), () -> "fresh"));
        cache.invalidate(); verifyNoInteractions(store);
    }

    @Test void simultaneousIdenticalReadsLoadOnce() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = java.util.stream.IntStream.range(0, 16).mapToObj(i -> pool.submit(() -> {
                try { return cache.get("user", mapper.constructType(String.class), () -> { loads.incrementAndGet(); return "fresh"; }); }
                catch (Throwable error) { throw new RuntimeException(error); }
            })).toList();
            for (var task : tasks) assertEquals("fresh", task.get());
        }
        assertEquals(1, loads.get());
    }
}
