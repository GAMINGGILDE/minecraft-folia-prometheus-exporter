package de.minecraftgilde.prometheus.minecraft.event;

import de.minecraftgilde.prometheus.MetricsCore;
import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.minecraft.RateLimitedFailureReporter;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Wires the event collector into the existing Metrics Core. */
public final class EventRuntime {

    public EventRuntime(
        MetricsCore core,
        Plugin plugin,
        ExporterConfiguration configuration,
        Clock clock
    ) {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(clock, "clock");

        RateLimitedFailureReporter failures = new RateLimitedFailureReporter(
            plugin.getLogger(),
            configuration.logging().collectionErrors(),
            clock
        );
        register(
            core,
            configuration.collectors().events(),
            new BukkitEventRegistration(plugin),
            failure -> failures.accept("events", failure),
            () -> Bukkit.getWorlds().stream()
                .map(org.bukkit.World::getName)
                .toList()
        );
    }

    EventRuntime(
        MetricsCore core,
        boolean enabled,
        EventRegistration registration,
        Consumer<Throwable> failureListener
    ) {
        register(core, enabled, registration, failureListener, List::of);
    }

    private static void register(
        MetricsCore core,
        boolean enabled,
        EventRegistration registration,
        Consumer<Throwable> failureListener,
        Supplier<List<String>> loadedWorldLabels
    ) {
        Objects.requireNonNull(core, "core");
        core.collectorCoordinator().register(
            new EventCollector(
                core.registry(),
                enabled,
                Objects.requireNonNull(registration, "registration"),
                Objects.requireNonNull(failureListener, "failureListener"),
                Objects.requireNonNull(loadedWorldLabels, "loadedWorldLabels")
            )
        );
    }
}
