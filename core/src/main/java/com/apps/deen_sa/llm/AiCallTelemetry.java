package com.apps.deen_sa.llm;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/** Low-cardinality production metrics for measuring AI cost before optimizing it. */
public final class AiCallTelemetry {
    private AiCallTelemetry() { }

    public static void success(String purpose, String model, long inputTokens, long cachedInputTokens,
                               long outputTokens, long startedNanos) {
        tagsCounter("deen.ai.calls", purpose, model, "success").increment();
        tagsCounter("deen.ai.tokens.input", purpose, model, "success").increment(inputTokens);
        tagsCounter("deen.ai.tokens.input.cached", purpose, model, "success").increment(cachedInputTokens);
        tagsCounter("deen.ai.tokens.output", purpose, model, "success").increment(outputTokens);
        Timer.builder("deen.ai.latency").tags("purpose", purpose, "model", model, "outcome", "success")
                .register(Metrics.globalRegistry).record(Duration.ofNanos(System.nanoTime() - startedNanos));
    }

    public static void failure(String purpose, String model, long startedNanos) {
        tagsCounter("deen.ai.calls", purpose, model, "failure").increment();
        Timer.builder("deen.ai.latency").tags("purpose", purpose, "model", model, "outcome", "failure")
                .register(Metrics.globalRegistry).record(Duration.ofNanos(System.nanoTime() - startedNanos));
    }

    public static void avoided(String reason) {
        Metrics.counter("deen.ai.calls.avoided", "reason", reason).increment();
    }

    private static io.micrometer.core.instrument.Counter tagsCounter(
            String name, String purpose, String model, String outcome) {
        return Metrics.counter(name, "purpose", purpose, "model", model, "outcome", outcome);
    }
}
