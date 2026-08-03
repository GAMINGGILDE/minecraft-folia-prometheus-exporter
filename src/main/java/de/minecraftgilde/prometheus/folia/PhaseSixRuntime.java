package de.minecraftgilde.prometheus.folia;

import de.minecraftgilde.prometheus.MetricsCore;
import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.minecraft.RateLimitedFailureReporter;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import de.minecraftgilde.prometheus.scheduler.PaperCollectionScheduler;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

/** Wires the capability-gated Phase-6 boundary without referencing its provider. */
public final class PhaseSixRuntime implements AutoCloseable {

    private final CollectionScheduler scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();

    public PhaseSixRuntime(
        MetricsCore core,
        Plugin plugin,
        ExporterConfiguration configuration,
        Clock clock
    ) {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(clock, "clock");
        Server server = plugin.getServer();
        scheduler = new PaperCollectionScheduler(plugin, server);
        RateLimitedFailureReporter failures = new RateLimitedFailureReporter(
            plugin.getLogger(),
            configuration.logging().collectionErrors(),
            clock
        );
        FoliaProviderContext context = new FoliaProviderContext(
            core.registry(),
            server,
            scheduler,
            configuration,
            clock,
            failures
        );
        core.collectorCoordinator().register(
            new FoliaCollector(
                configuration.collectors().folia(),
                () -> FoliaRegionCapability.isAvailable(Server.class),
                new ReflectiveFoliaProviderFactory(
                    plugin.getClass().getClassLoader()
                ),
                context,
                plugin.getLogger()::warning
            )
        );
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            scheduler.cancelAll();
        }
    }
}
