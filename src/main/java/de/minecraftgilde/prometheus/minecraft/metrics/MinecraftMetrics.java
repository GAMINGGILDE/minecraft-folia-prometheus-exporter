package de.minecraftgilde.prometheus.minecraft.metrics;

import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.minecraft.ServerSnapshot;
import de.minecraftgilde.prometheus.minecraft.WorldChunkSnapshot;
import de.minecraftgilde.prometheus.minecraft.WorldSizeSnapshot;
import de.minecraftgilde.prometheus.minecraft.WorldSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Owns Minecraft snapshot repositories and idempotent private-registry registration. */
public final class MinecraftMetrics {

    private final PrometheusRegistry registry;
    private final ExporterConfiguration configuration;
    private final SnapshotRepository<ServerSnapshot> serverRepository =
        new SnapshotRepository<>();
    private final SnapshotRepository<WorldSnapshot> worldRepository =
        new SnapshotRepository<>();
    private final SnapshotRepository<WorldChunkSnapshot> chunkRepository =
        new SnapshotRepository<>();
    private final SnapshotRepository<WorldSizeSnapshot> worldSizeRepository =
        new SnapshotRepository<>();
    private final List<MultiCollector> registeredCollectors = new ArrayList<>();
    private boolean registered;
    private RuntimeException registrationFailure;

    public MinecraftMetrics(
        PrometheusRegistry registry,
        ExporterConfiguration configuration
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public synchronized void register() {
        if (registered) {
            return;
        }
        if (registrationFailure != null) {
            throw new IllegalStateException(
                "Minecraft metric registration previously failed",
                registrationFailure
            );
        }
        try {
            if (configuration.collectors().server()) {
                register(
                    new ServerMetricsCollector(
                        serverRepository,
                        configuration.collectors().pluginInfo()
                    )
                );
            }
            if (configuration.collectors().worlds()) {
                register(new WorldMetricsCollector(worldRepository));
            }
            if (configuration.collectors().chunks()) {
                register(new ChunkMetricsCollector(chunkRepository));
            }
            if (
                configuration.collectors().filesystem()
                    && configuration.filesystem().includeWorldSizes()
            ) {
                register(new WorldSizeMetricsCollector(worldSizeRepository));
            }
            registered = true;
        } catch (RuntimeException failure) {
            List<MultiCollector> reverse = new ArrayList<>(registeredCollectors);
            Collections.reverse(reverse);
            reverse.forEach(registry::unregister);
            registeredCollectors.clear();
            registrationFailure = failure;
            throw failure;
        }
    }

    public SnapshotRepository<ServerSnapshot> serverRepository() {
        return serverRepository;
    }

    public SnapshotRepository<WorldSnapshot> worldRepository() {
        return worldRepository;
    }

    public SnapshotRepository<WorldChunkSnapshot> chunkRepository() {
        return chunkRepository;
    }

    public SnapshotRepository<WorldSizeSnapshot> worldSizeRepository() {
        return worldSizeRepository;
    }

    private void register(MultiCollector collector) {
        registry.register(collector);
        registeredCollectors.add(collector);
    }
}
