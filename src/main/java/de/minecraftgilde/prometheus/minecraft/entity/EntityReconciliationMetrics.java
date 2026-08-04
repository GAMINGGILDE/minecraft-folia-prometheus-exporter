package de.minecraftgilde.prometheus.minecraft.entity;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Bounded runtime metrics for successful entity reconciliation runs. */
final class EntityReconciliationMetrics {

    private final Gauge duration;
    private final Gauge lastSuccess;
    private final Counter corrections;

    EntityReconciliationMetrics(PrometheusRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        List<Object> registered = new ArrayList<>();
        try {
            duration = Gauge.builder()
                .name("minecraft_entity_reconciliation_duration_seconds")
                .help("Duration of the last successful entity reconciliation.")
                .register(registry);
            registered.add(duration);
            lastSuccess = Gauge.builder()
                .name(
                    "minecraft_entity_reconciliation_last_success_timestamp_seconds"
                )
                .help(
                    "Unix timestamp of the last successful entity reconciliation."
                )
                .register(registry);
            registered.add(lastSuccess);
            corrections = Counter.builder()
                .name("minecraft_entity_reconciliation_corrections_total")
                .help(
                    "Total differing aggregate values corrected by full entity reconciliations."
                )
                .register(registry);
            registered.add(corrections);
            corrections.inc(0.0);
        } catch (RuntimeException failure) {
            List<Object> reverse = new ArrayList<>(registered);
            Collections.reverse(reverse);
            reverse.forEach(metric -> unregister(registry, metric));
            throw failure;
        }
    }

    void recordSuccess(
        Duration elapsed,
        Instant capturedAt,
        long correctionCount
    ) {
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (elapsed.isNegative() || correctionCount < 0L) {
            throw new IllegalArgumentException(
                "Reconciliation runtime values must not be negative"
            );
        }
        duration.set(elapsed.toNanos() / 1_000_000_000.0);
        lastSuccess.set(
            capturedAt.getEpochSecond() + capturedAt.getNano() / 1_000_000_000.0
        );
        if (correctionCount > 0L) {
            corrections.inc(correctionCount);
        }
    }

    private static void unregister(PrometheusRegistry registry, Object metric) {
        if (metric instanceof Gauge gauge) {
            registry.unregister(gauge);
        } else if (metric instanceof Counter counter) {
            registry.unregister(counter);
        }
    }
}
