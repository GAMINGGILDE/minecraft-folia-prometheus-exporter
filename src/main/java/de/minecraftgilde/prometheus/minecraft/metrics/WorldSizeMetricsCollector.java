package de.minecraftgilde.prometheus.minecraft.metrics;

import de.minecraftgilde.prometheus.minecraft.WorldSizeSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricFamilyDescriptor;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import java.util.List;
import java.util.Objects;

/** Exposes the latest complete asynchronous world-directory sizes. */
final class WorldSizeMetricsCollector implements MultiCollector {

    private final SnapshotRepository<WorldSizeSnapshot> repository;
    private final MetricFamilyDescriptor worldSize = MetricFamilyDescriptor
        .gauge("minecraft_world_size_bytes")
        .help("Regular-file bytes below each world directory without following symlinks.")
        .labelNames("world")
        .build();

    WorldSizeMetricsCollector(SnapshotRepository<WorldSizeSnapshot> repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public MetricSnapshots collect() {
        List<WorldSizeSnapshot> worlds = repository
            .current()
            .map(snapshot -> snapshot.values())
            .orElseGet(List::of);
        return MetricSnapshots.of(
            MetricSnapshotFactory.gauge(
                worldSize,
                worlds
                    .stream()
                    .map(world -> new MetricSnapshotFactory.Value(
                        world.sizeBytes(),
                        Labels.of("world", world.world())
                    ))
                    .toList()
            )
        );
    }

    @Override
    public List<MetricFamilyDescriptor> getMetricFamilyDescriptors() {
        return List.of(worldSize);
    }
}
