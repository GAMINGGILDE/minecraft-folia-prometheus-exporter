package de.minecraftgilde.prometheus.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExporterMetricsTest {

    @Test
    void repeatedRegistrationUsesOneMetricOwnerWithoutDuplicates() {
        PrometheusRegistry registry = new PrometheusRegistry();

        ExporterMetrics first = ExporterMetrics.register(
            registry,
            "test",
            "unknown",
            "common"
        );
        ExporterMetrics second = ExporterMetrics.register(
            registry,
            "test",
            "unknown",
            "common"
        );

        assertSame(first, second);
        List<String> names = registry
            .scrape()
            .stream()
            .map(snapshot -> snapshot.getMetadata().getPrometheusName())
            .toList();
        assertEquals(names.size(), names.stream().distinct().count());
    }
}
