package de.minecraftgilde.prometheus;

import io.prometheus.metrics.model.registry.PrometheusRegistry;

/** Test-only access to the package-private, MetricsCore-owned metric set. */
public final class ExporterMetricsTestSupport {

    private ExporterMetricsTestSupport() {}

    public static ExporterMetrics create(PrometheusRegistry registry) {
        return new ExporterMetrics(registry, "test", "unknown", "common");
    }
}
