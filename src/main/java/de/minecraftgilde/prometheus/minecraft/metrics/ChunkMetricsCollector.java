package de.minecraftgilde.prometheus.minecraft.metrics;

import de.minecraftgilde.prometheus.minecraft.WorldChunkSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricFamilyDescriptor;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import java.util.List;
import java.util.Objects;

/** Exposes only the aggregate currently-loaded chunk count. */
final class ChunkMetricsCollector implements MultiCollector {

    private final SnapshotRepository<WorldChunkSnapshot> repository;
    private final MetricFamilyDescriptor loadedChunks = MetricFamilyDescriptor
        .gauge("minecraft_world_loaded_chunks")
        .help("Number of currently loaded chunks in each loaded world.")
        .labelNames("world")
        .build();

    ChunkMetricsCollector(SnapshotRepository<WorldChunkSnapshot> repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public MetricSnapshots collect() {
        List<WorldChunkSnapshot> worlds = repository
            .current()
            .map(snapshot -> snapshot.values())
            .orElseGet(List::of);
        return MetricSnapshots.of(
            MetricSnapshotFactory.gauge(
                loadedChunks,
                worlds
                    .stream()
                    .map(world -> new MetricSnapshotFactory.Value(
                        world.loadedChunks(),
                        Labels.of("world", world.world())
                    ))
                    .toList()
            )
        );
    }

    @Override
    public List<MetricFamilyDescriptor> getMetricFamilyDescriptors() {
        return List.of(loadedChunks);
    }
}
