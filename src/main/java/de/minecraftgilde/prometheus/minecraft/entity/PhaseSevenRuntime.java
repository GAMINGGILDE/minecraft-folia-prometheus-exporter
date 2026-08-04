package de.minecraftgilde.prometheus.minecraft.entity;

import de.minecraftgilde.prometheus.MetricsCore;
import de.minecraftgilde.prometheus.collector.PeriodicSnapshotCollector;
import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.minecraft.RateLimitedFailureReporter;
import de.minecraftgilde.prometheus.minecraft.metrics.EntityMetricsCollector;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import de.minecraftgilde.prometheus.scheduler.PaperCollectionScheduler;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

/** Wires the Phase-7 hybrid entity collector into the existing Metrics Core. */
public final class PhaseSevenRuntime implements AutoCloseable {

    private final CollectionScheduler scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();

    public PhaseSevenRuntime(
        MetricsCore core,
        Plugin plugin,
        ExporterConfiguration configuration,
        Clock clock
    ) {
        this(
            core,
            Objects.requireNonNull(plugin, "plugin").getServer(),
            new PaperCollectionScheduler(plugin, plugin.getServer()),
            new BukkitEntityEventRegistration(plugin),
            configuration,
            clock,
            System::nanoTime,
            plugin.getLogger()
        );
    }

    PhaseSevenRuntime(
        MetricsCore core,
        Server server,
        CollectionScheduler scheduler,
        EntityEventRegistration registration,
        ExporterConfiguration configuration,
        Clock clock,
        java.util.function.LongSupplier nanoTime,
        Logger logger
    ) {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(nanoTime, "nanoTime");
        Objects.requireNonNull(logger, "logger");

        boolean enabled = configuration.collectors().entities();
        SnapshotRepository<EntityWorldSnapshot> repository =
            new SnapshotRepository<>();
        EntityStateStore stateStore = new EntityStateStore(
            repository,
            clock,
            configuration.entities().includeExactTypes()
        );
        EntityGroupClassifier classifier = new EntityGroupClassifier();
        RateLimitedFailureReporter failures = new RateLimitedFailureReporter(
            logger,
            configuration.logging().collectionErrors(),
            clock
        );

        EntityReconciliationMetrics reconciliationMetrics = null;
        EntityMetricsCollector metricCollector = null;
        if (enabled) {
            PrometheusRegistry registry = core.registry();
            metricCollector = new EntityMetricsCollector(
                repository,
                configuration.entities().includeExactTypes(),
                configuration.entities().includeProjectileTotal()
            );
            registry.register(metricCollector);
            try {
                reconciliationMetrics = new EntityReconciliationMetrics(registry);
            } catch (RuntimeException failure) {
                registry.unregister(metricCollector);
                throw failure;
            }
        }

        EntityReconciliationMetrics runtimeMetrics = reconciliationMetrics;
        BiConsumer<String, Throwable> failureReporter = (key, failure) -> {
            try {
                failures.accept(key, failure);
            } catch (RuntimeException ignored) {
                // A diagnostic observer cannot escape a scheduler callback.
            }
        };
        BukkitEntityReconciliationCapture capture =
            new BukkitEntityReconciliationCapture(
                server,
                scheduler,
                stateStore,
                classifier,
                nanoTime,
                failureReporter
            );
        PeriodicSnapshotCollector<EntityScanResult> reconciliation =
            new PeriodicSnapshotCollector<>(
                "entity-reconciliation",
                true,
                scheduler,
                configuration.entities().reconciliationInterval(),
                configuration.entities().reconciliationTimeout(),
                capture,
                (capturedAt, values) -> {
                    if (values.size() != 1) {
                        throw new IllegalStateException(
                            "Entity reconciliation produced an invalid result count"
                        );
                    }
                    EntityStateStore.ReconciliationCommit commit = stateStore.commit(
                        values.getFirst(),
                        capturedAt
                    );
                    if (runtimeMetrics != null) {
                        runtimeMetrics.recordSuccess(
                            commit.duration(),
                            capturedAt,
                            commit.corrections()
                        );
                    }
                },
                clock,
                failures
            );
        core.collectorCoordinator().register(
            new EntityCollector(
                enabled,
                registration,
                stateStore,
                classifier,
                reconciliation,
                failure -> failures.accept("entity-events", failure)
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
