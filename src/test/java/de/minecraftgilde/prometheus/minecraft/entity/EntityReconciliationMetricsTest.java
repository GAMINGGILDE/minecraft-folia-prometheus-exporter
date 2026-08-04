package de.minecraftgilde.prometheus.minecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EntityReconciliationMetricsTest {

    @Test
    void recordsOnlyThreeBoundedUnlabelledRuntimeFamilies() {
        PrometheusRegistry registry = new PrometheusRegistry();
        EntityReconciliationMetrics metrics = new EntityReconciliationMetrics(
            registry
        );

        metrics.recordSuccess(
            Duration.ofMillis(250),
            Instant.ofEpochSecond(100, 500_000_000),
            3L
        );

        assertEquals(
            0.25,
            value(metric(
                registry,
                "minecraft_entity_reconciliation_duration_seconds"
            ))
        );
        assertEquals(
            100.5,
            value(metric(
                registry,
                "minecraft_entity_reconciliation_last_success_timestamp_seconds"
            ))
        );
        assertEquals(
            3.0,
            value(metric(
                registry,
                "minecraft_entity_reconciliation_corrections_total"
            ))
        );
        assertTrue(
            registry.scrape()
                .stream()
                .filter(snapshot -> snapshot
                    .getMetadata()
                    .getExpositionBasePrometheusName()
                    .startsWith("minecraft_entity_reconciliation"))
                .allMatch(snapshot -> snapshot
                    .getDataPoints()
                    .stream()
                    .allMatch(point -> point.getLabels().isEmpty()))
        );
    }

    private static MetricSnapshot metric(
        PrometheusRegistry registry,
        String name
    ) {
        return registry.scrape()
            .stream()
            .filter(snapshot -> snapshot
                .getMetadata()
                .getExpositionBasePrometheusName()
                .equals(name))
            .findFirst()
            .orElseThrow();
    }

    private static double value(MetricSnapshot snapshot) {
        if (snapshot instanceof GaugeSnapshot gauge) {
            return gauge.getDataPoints().getFirst().getValue();
        }
        return ((CounterSnapshot) snapshot)
            .getDataPoints()
            .getFirst()
            .getValue();
    }
}
