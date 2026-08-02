package de.minecraftgilde.prometheus.minecraft;

import de.minecraftgilde.prometheus.MetricsCore;
import de.minecraftgilde.prometheus.collector.PeriodicSnapshotCollector;
import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.minecraft.metrics.MinecraftMetrics;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import de.minecraftgilde.prometheus.scheduler.PaperCollectionScheduler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

/** Wires Phase-4 collectors into the existing Metrics Core and coordinator. */
public final class PhaseFourRuntime implements AutoCloseable {

    private final CollectionScheduler scheduler;
    private final BukkitWorldSizeSnapshotCapture worldSizeCapture;
    private final AtomicBoolean closed = new AtomicBoolean();

    public PhaseFourRuntime(
        MetricsCore core,
        Plugin plugin,
        ExporterConfiguration configuration,
        Instant activationTime,
        Clock clock
    ) {
        this(
            core,
            Objects.requireNonNull(plugin, "plugin").getServer(),
            new PaperCollectionScheduler(plugin, plugin.getServer()),
            new WorldSizeCalculator(),
            configuration,
            activationTime,
            clock,
            plugin.getLogger()
        );
    }

    PhaseFourRuntime(
        MetricsCore core,
        Server server,
        CollectionScheduler scheduler,
        WorldSizeCalculation worldSizeCalculator,
        ExporterConfiguration configuration,
        Instant activationTime,
        Clock clock,
        Logger logger
    ) {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(worldSizeCalculator, "worldSizeCalculator");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(activationTime, "activationTime");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(logger, "logger");

        RateLimitedFailureReporter failures = new RateLimitedFailureReporter(
            logger,
            configuration.logging().collectionErrors(),
            clock
        );
        MinecraftMetrics metrics = new MinecraftMetrics(
            core.registry(),
            configuration
        );
        metrics.register();

        worldSizeCapture = new BukkitWorldSizeSnapshotCapture(
            server,
            scheduler,
            worldSizeCalculator,
            metrics.worldSizeRepository(),
            failure -> failures.accept("world-sizes", failure),
            configuration.filesystem().worldSizeScanConcurrency()
        );
        core.collectorCoordinator().register(
            new PeriodicSnapshotCollector<>(
                "server",
                configuration.collectors().server(),
                scheduler,
                configuration.collection().serverInterval(),
                configuration.collection().timeout(),
                new BukkitServerSnapshotCapture(
                    server,
                    scheduler,
                    clock,
                    activationTime,
                    configuration.collectors().pluginInfo()
                ),
                metrics.serverRepository(),
                clock,
                failures
            )
        );
        core.collectorCoordinator().register(
            new PeriodicSnapshotCollector<>(
                "worlds",
                configuration.collectors().worlds(),
                scheduler,
                configuration.collection().worldInterval(),
                configuration.collection().timeout(),
                new BukkitWorldSnapshotCapture(
                    server,
                    metrics.worldRepository(),
                    failure -> failures.accept("worlds", failure)
                ),
                metrics.worldRepository(),
                clock,
                failures
            )
        );
        core.collectorCoordinator().register(
            new PeriodicSnapshotCollector<>(
                "chunks",
                configuration.collectors().chunks(),
                scheduler,
                configuration.collection().worldInterval(),
                configuration.collection().timeout(),
                new BukkitChunkSnapshotCapture(
                    server,
                    metrics.chunkRepository(),
                    failure -> failures.accept("chunks", failure)
                ),
                metrics.chunkRepository(),
                clock,
                failures
            )
        );
        boolean worldSizesEnabled = configuration.collectors().filesystem()
            && configuration.filesystem().includeWorldSizes();
        core.collectorCoordinator().register(
            new PeriodicSnapshotCollector<>(
                "world-sizes",
                worldSizesEnabled,
                scheduler,
                configuration.collection().filesystemInterval(),
                configuration.collection().filesystemTimeout(),
                worldSizeCapture,
                metrics.worldSizeRepository(),
                clock,
                failures
            )
        );
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            worldSizeCapture.close();
            scheduler.cancelAll();
        }
    }
}
