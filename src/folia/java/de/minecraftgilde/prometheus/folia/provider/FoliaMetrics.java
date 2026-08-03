package de.minecraftgilde.prometheus.folia.provider;

import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Owns the private-registry collector and repository for Phase 6. */
final class FoliaMetrics {

    private final PrometheusRegistry registry;
    private final FoliaRegionMetricsCollector collector;
    private final SnapshotRepository<RegionObservation> repository =
        new SnapshotRepository<>();
    private boolean registered;

    FoliaMetrics(
        PrometheusRegistry registry,
        Clock clock,
        Duration ttl,
        List<TpsWindow> windows,
        List<String> statistics,
        List<TpsThreshold> thresholds
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        collector = new FoliaRegionMetricsCollector(
            repository,
            clock,
            ttl,
            windows,
            statistics,
            thresholds
        );
    }

    synchronized void register() {
        if (!registered) {
            registry.register(collector);
            registered = true;
        }
    }

    SnapshotRepository<RegionObservation> repository() {
        return repository;
    }
}
