package de.minecraftgilde.prometheus.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class JvmMetricsRegistrarTest {

    @Test
    void registersConfiguredOfficialInstrumentationExactlyOnce() {
        PrometheusRegistry registry = new PrometheusRegistry();
        JvmMetricsRegistrar registrar = new JvmMetricsRegistrar(
            registry,
            true,
            true
        );

        registrar.register();
        List<String> firstRegistration = metricNames(registry);
        registrar.register();

        assertEquals(firstRegistration, metricNames(registry));
        assertEquals(
            firstRegistration.size(),
            firstRegistration.stream().distinct().count()
        );
        assertTrue(firstRegistration.contains("jvm_memory_used_bytes"));
        assertTrue(firstRegistration.contains("jvm_gc_collection_seconds"));
        assertTrue(firstRegistration.contains("jvm_threads_current"));
        assertTrue(firstRegistration.contains("jvm_classes_currently_loaded"));
        assertTrue(firstRegistration.contains("jvm_buffer_pool_used_bytes"));
        assertTrue(firstRegistration.contains("process_cpu_seconds"));
    }

    @Test
    void keepsJvmAndProcessGroupsIndependentlyConfigurable() {
        PrometheusRegistry jvmOnlyRegistry = new PrometheusRegistry();
        new JvmMetricsRegistrar(jvmOnlyRegistry, true, false).register();
        List<String> jvmOnlyNames = metricNames(jvmOnlyRegistry);
        assertTrue(jvmOnlyNames.stream().anyMatch(name -> name.startsWith("jvm_")));
        assertFalse(
            jvmOnlyNames.stream().anyMatch(name -> name.startsWith("process_"))
        );

        PrometheusRegistry processOnlyRegistry = new PrometheusRegistry();
        new JvmMetricsRegistrar(processOnlyRegistry, false, true).register();
        List<String> processOnlyNames = metricNames(processOnlyRegistry);
        assertFalse(
            processOnlyNames.stream().anyMatch(name -> name.startsWith("jvm_"))
        );
        assertTrue(
            processOnlyNames.stream().anyMatch(name -> name.startsWith("process_"))
        );

        PrometheusRegistry disabledRegistry = new PrometheusRegistry();
        new JvmMetricsRegistrar(disabledRegistry, false, false).register();
        assertTrue(metricNames(disabledRegistry).isEmpty());
    }

    @Test
    void leavesTheGlobalDefaultRegistryUntouched() {
        List<String> defaultNamesBefore = metricNames(
            PrometheusRegistry.defaultRegistry
        );
        PrometheusRegistry privateRegistry = new PrometheusRegistry();

        new JvmMetricsRegistrar(privateRegistry, true, true).register();

        assertEquals(
            defaultNamesBefore,
            metricNames(PrometheusRegistry.defaultRegistry)
        );
        assertFalse(metricNames(privateRegistry).isEmpty());
    }

    @Test
    void propagatesRegistrationFailures() {
        PrometheusRegistry registry = new PrometheusRegistry();
        Gauge.builder()
            .name("jvm_memory_used_bytes")
            .help("Intentional duplicate for the registration failure test.")
            .register(registry);
        JvmMetricsRegistrar registrar = new JvmMetricsRegistrar(
            registry,
            true,
            false
        );

        RuntimeException failure = assertThrows(
            RuntimeException.class,
            registrar::register
        );
        IllegalStateException repeatedFailure = assertThrows(
            IllegalStateException.class,
            registrar::register
        );

        assertSame(failure, repeatedFailure.getCause());
        assertTrue(metricNames(registry).isEmpty());
    }

    private static List<String> metricNames(PrometheusRegistry registry) {
        return registry
            .scrape()
            .stream()
            .map(snapshot -> snapshot.getMetadata().getPrometheusName())
            .toList();
    }
}
