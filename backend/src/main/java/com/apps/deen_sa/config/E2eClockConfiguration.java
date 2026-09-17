package com.apps.deen_sa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only controllable clock. It is loaded exclusively by the e2e profile. */
@Configuration
@Profile("e2e")
public class E2eClockConfiguration {
    @Bean Clock systemClock() { return new AdjustableClock(Instant.parse("2026-09-17T09:00:00Z")); }

    public static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> instant;
        AdjustableClock(Instant initial) { instant = new AtomicReference<>(initial); }
        public void set(Instant value) { instant.set(value); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
