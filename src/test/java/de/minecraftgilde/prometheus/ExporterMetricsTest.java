package de.minecraftgilde.prometheus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExporterMetricsTest {

    @Test
    void separateMetricsCoresRegisterOnceInTheirPrivateRegistries() {
        try (
            MetricsCore first = core();
            MetricsCore second = core()
        ) {
            assertNotSame(first.registry(), second.registry());
            assertUniqueMetricNames(first);
            assertUniqueMetricNames(second);
        }
    }

    private static MetricsCore core() {
        return new MetricsCore(
            "test",
            "unknown",
            "common",
            (name, failure) -> {}
        );
    }

    private static void assertUniqueMetricNames(MetricsCore core) {
        List<String> names = core.registry()
            .scrape()
            .stream()
            .map(snapshot -> snapshot.getMetadata().getPrometheusName())
            .toList();
        assertEquals(names.size(), names.stream().distinct().count());
    }
}
