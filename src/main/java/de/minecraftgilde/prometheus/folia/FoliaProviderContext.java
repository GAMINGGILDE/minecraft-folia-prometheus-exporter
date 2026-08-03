package de.minecraftgilde.prometheus.folia;

import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.time.Clock;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.bukkit.Server;

/** Constructor-injected services that contain no Folia-only API references. */
public record FoliaProviderContext(
    PrometheusRegistry registry,
    Server server,
    CollectionScheduler scheduler,
    ExporterConfiguration configuration,
    Clock clock,
    BiConsumer<String, Throwable> failureListener
) {

    public FoliaProviderContext {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(failureListener, "failureListener");
    }
}
