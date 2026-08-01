package de.minecraftgilde.prometheus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            assertPhaseThreeMetrics(first);
            assertPhaseThreeMetrics(second);
        }
    }

    @Test
    void disabledJvmAndProcessGroupsAreNotRegistered() {
        try (
            MetricsCore core = new MetricsCore(
                "test",
                "unknown",
                "common",
                false,
                false,
                (name, failure) -> {}
            )
        ) {
            List<String> names = metricNames(core);
            assertFalsePrefix(names, "jvm_");
            assertFalsePrefix(names, "process_");
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
        List<String> names = metricNames(core);
        assertEquals(names.size(), names.stream().distinct().count());
    }

    private static void assertPhaseThreeMetrics(MetricsCore core) {
        List<String> names = metricNames(core);
        assertTrue(names.contains("jvm_memory_used_bytes"));
        assertTrue(names.contains("jvm_gc_collection_seconds"));
        assertTrue(names.contains("jvm_threads_current"));
        assertTrue(names.contains("jvm_classes_currently_loaded"));
        assertTrue(names.contains("jvm_buffer_pool_used_bytes"));
        assertTrue(names.contains("process_start_time_seconds"));
    }

    private static void assertFalsePrefix(List<String> names, String prefix) {
        assertTrue(names.stream().noneMatch(name -> name.startsWith(prefix)));
    }

    private static List<String> metricNames(MetricsCore core) {
        return core.registry()
            .scrape()
            .stream()
            .map(snapshot -> snapshot.getMetadata().getPrometheusName())
            .toList();
    }
}
