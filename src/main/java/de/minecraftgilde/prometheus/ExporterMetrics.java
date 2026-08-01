package de.minecraftgilde.prometheus;

import de.minecraftgilde.prometheus.collector.CollectorState;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.core.metrics.Info;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.util.Objects;

/** Low-cardinality self-observation metrics owned by one Metrics Core. */
public final class ExporterMetrics {

    private final Gauge ready;
    private final Gauge health;
    private final Counter scrapes;
    private final Counter scrapeErrors;
    private final Counter httpRequests;
    private final Gauge collectorState;

    ExporterMetrics(
        PrometheusRegistry registry,
        String version,
        String gitCommit,
        String provider
    ) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(gitCommit, "gitCommit");
        Objects.requireNonNull(provider, "provider");

        Info buildInfo = Info.builder()
            .name("minecraft_exporter_build_info")
            .help("Build information for the Minecraft Prometheus exporter.")
            .labelNames("version", "git_commit", "provider")
            .register(registry);
        buildInfo.setLabelValues(version, gitCommit, provider);

        ready = Gauge.builder()
            .name("minecraft_exporter_ready")
            .help("Whether all mandatory exporter components are ready for scrapes.")
            .register(registry);
        ready.set(0.0);

        health = Gauge.builder()
            .name("minecraft_exporter_health")
            .help("Whether the exporter HTTP service is fundamentally healthy.")
            .register(registry);
        health.set(0.0);

        scrapes = Counter.builder()
            .name("minecraft_exporter_scrapes_total")
            .help("Total Prometheus scrape attempts served by the exporter.")
            .register(registry);

        scrapeErrors = Counter.builder()
            .name("minecraft_exporter_scrape_errors_total")
            .help("Total Prometheus scrape attempts that returned a server error.")
            .register(registry);

        httpRequests = Counter.builder()
            .name("minecraft_exporter_http_requests_total")
            .help("HTTP requests by bounded endpoint and response status class.")
            .labelNames("endpoint", "status_class")
            .register(registry);

        collectorState = Gauge.builder()
            .name("minecraft_exporter_collector_state")
            .help("One-hot lifecycle state for each registered internal collector.")
            .labelNames("collector", "state")
            .register(registry);
    }

    public void setReady(boolean value) {
        ready.set(value ? 1.0 : 0.0);
    }

    public void setHealthy(boolean value) {
        health.set(value ? 1.0 : 0.0);
    }

    public void recordScrapeAttempt() {
        scrapes.inc();
    }

    public void recordScrapeError() {
        scrapeErrors.inc();
    }

    public void recordHttpRequest(String endpoint, int statusCode) {
        httpRequests
            .labelValues(endpoint, statusClass(statusCode))
            .inc();
    }

    public void updateCollectorState(String collector, CollectorState current) {
        Objects.requireNonNull(collector, "collector");
        Objects.requireNonNull(current, "current");
        for (CollectorState state : CollectorState.values()) {
            collectorState
                .labelValues(collector, state.metricValue())
                .set(state == current ? 1.0 : 0.0);
        }
    }

    private static String statusClass(int statusCode) {
        return switch (statusCode / 100) {
            case 2 -> "2xx";
            case 4 -> "4xx";
            case 5 -> "5xx";
            default -> "other";
        };
    }
}
