package de.minecraftgilde.prometheus;

import de.minecraftgilde.prometheus.collector.CollectorCoordinator;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.HttpConfiguration;
import de.minecraftgilde.prometheus.http.MetricsHttpServer;
import de.minecraftgilde.prometheus.metrics.ExporterMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** Owns the registry, collector coordinator, readiness state, and HTTP lifecycle. */
public final class MetricsCore implements AutoCloseable {

    private final PrometheusRegistry registry = new PrometheusRegistry();
    private final ExporterLifecycleState lifecycleState =
        new ExporterLifecycleState();
    private final ExporterMetrics exporterMetrics;
    private final CollectorCoordinator collectorCoordinator;
    private final AtomicBoolean closed = new AtomicBoolean();
    private MetricsHttpServer httpServer;

    public MetricsCore(
        String version,
        String gitCommit,
        String provider,
        BiConsumer<String, Throwable> collectorFailureListener
    ) {
        exporterMetrics = ExporterMetrics.register(
            registry,
            Objects.requireNonNull(version, "version"),
            Objects.requireNonNull(gitCommit, "gitCommit"),
            Objects.requireNonNull(provider, "provider")
        );
        collectorCoordinator = new CollectorCoordinator(
            exporterMetrics::updateCollectorState,
            Objects.requireNonNull(
                collectorFailureListener,
                "collectorFailureListener"
            )
        );
        lifecycleState.setRegistryAvailable(true);
    }

    public synchronized void start(HttpConfiguration configuration)
        throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        if (closed.get()) {
            throw new IllegalStateException("Metrics Core is already closed");
        }
        if (httpServer != null) {
            return;
        }

        lifecycleState.setMetricsCoreStarted(true);
        collectorCoordinator.startAll();
        try {
            httpServer = MetricsHttpServer.start(
                configuration,
                registry,
                lifecycleState,
                exporterMetrics
            );
            lifecycleState.setInitializationComplete(true);
            exporterMetrics.setReady(lifecycleState.isReady());
        } catch (IOException | RuntimeException exception) {
            lifecycleState.setInitializationComplete(false);
            lifecycleState.setMetricsCoreStarted(false);
            exporterMetrics.setReady(false);
            collectorCoordinator.stopAll();
            throw exception;
        }
    }

    public PrometheusRegistry registry() {
        return registry;
    }

    public CollectorCoordinator collectorCoordinator() {
        return collectorCoordinator;
    }

    public ExporterLifecycleState lifecycleState() {
        return lifecycleState;
    }

    public synchronized int httpPort() {
        if (httpServer == null) {
            throw new IllegalStateException("HTTP server is not running");
        }
        return httpServer.port();
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        lifecycleState.setInitializationComplete(false);
        exporterMetrics.setReady(false);
        if (httpServer != null) {
            httpServer.close();
            httpServer = null;
        }
        collectorCoordinator.stopAll();
        lifecycleState.setMetricsCoreStarted(false);
        lifecycleState.setRegistryAvailable(false);
        exporterMetrics.setHealthy(false);
        registry.clear();
    }
}
