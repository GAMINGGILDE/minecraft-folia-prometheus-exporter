package de.minecraftgilde.prometheus.jvm;

import io.prometheus.metrics.instrumentation.jvm.JvmBufferPoolMetrics;
import io.prometheus.metrics.instrumentation.jvm.JvmClassLoadingMetrics;
import io.prometheus.metrics.instrumentation.jvm.JvmGarbageCollectorMetrics;
import io.prometheus.metrics.instrumentation.jvm.JvmMemoryMetrics;
import io.prometheus.metrics.instrumentation.jvm.JvmThreadsMetrics;
import io.prometheus.metrics.instrumentation.jvm.ProcessMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.util.Objects;

/** Registers official JVM and process instrumentation in one private registry. */
public final class JvmMetricsRegistrar {

    private final PrometheusRegistry registry;
    private final boolean jvmMetricsEnabled;
    private final boolean processMetricsEnabled;
    private boolean registered;
    private RuntimeException registrationFailure;

    public JvmMetricsRegistrar(
        PrometheusRegistry registry,
        boolean jvmMetricsEnabled,
        boolean processMetricsEnabled
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.jvmMetricsEnabled = jvmMetricsEnabled;
        this.processMetricsEnabled = processMetricsEnabled;
    }

    /** Registers each configured official instrumentation at most once. */
    public synchronized void register() {
        if (registered) {
            return;
        }
        if (registrationFailure != null) {
            throw new IllegalStateException(
                "JVM/process metric registration previously failed",
                registrationFailure
            );
        }

        try {
            if (jvmMetricsEnabled) {
                JvmMemoryMetrics.builder().register(registry);
                JvmGarbageCollectorMetrics.builder().register(registry);
                JvmThreadsMetrics.builder().register(registry);
                JvmClassLoadingMetrics.builder().register(registry);
                JvmBufferPoolMetrics.builder().register(registry);
            }
            if (processMetricsEnabled) {
                ProcessMetrics.builder().register(registry);
            }
        } catch (RuntimeException failure) {
            registry.clear();
            registrationFailure = failure;
            throw failure;
        }

        registered = true;
    }
}
