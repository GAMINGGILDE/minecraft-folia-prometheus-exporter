package de.minecraftgilde.prometheus.minecraft.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.MetricsCore;
import de.minecraftgilde.prometheus.collector.CollectorState;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;

class PhaseFiveRuntimeTest {

    @Test
    void disabledConfigurationKeepsCollectorVisibleButRegistersNoEvents()
        throws Exception {
        AtomicInteger registrations = new AtomicInteger();
        EventRegistration registration = registration(registrations);
        try (MetricsCore core = core()) {
            new PhaseFiveRuntime(core, false, registration, failure -> {});

            core.collectorCoordinator().startAll();

            assertEquals(
                CollectorState.DISABLED,
                core.collectorCoordinator().states().get("events")
            );
            assertEquals(0, registrations.get());
            assertTrue(
                core.registry().scrape().stream().noneMatch(metric -> metric
                    .getMetadata()
                    .getExpositionBasePrometheusName()
                    .startsWith("minecraft_login_"))
            );
        }
    }

    @Test
    void enabledConfigurationRegistersOneManagedListenerAndAllFamilies()
        throws Exception {
        AtomicInteger registrations = new AtomicInteger();
        EventRegistration registration = registration(registrations);
        try (MetricsCore core = core()) {
            new PhaseFiveRuntime(core, true, registration, failure -> {});

            core.collectorCoordinator().startAll();
            core.collectorCoordinator().startAll();

            assertEquals(CollectorState.RUNNING, core.collectorCoordinator().states().get("events"));
            assertEquals(1, registrations.get());
            assertTrue(hasFamily(core, "minecraft_login_attempts_total"));
            assertTrue(hasFamily(core, "minecraft_chunks_generated_total"));
            assertFalse(hasFamily(core, "minecraft_commands_total"));
        }
    }

    private static EventRegistration registration(AtomicInteger registrations) {
        return new EventRegistration() {
            @Override
            public void register(Listener listener) {
                registrations.incrementAndGet();
            }

            @Override
            public void unregister(Listener listener) {}
        };
    }

    private static boolean hasFamily(MetricsCore core, String name) {
        return core.registry().scrape().stream().anyMatch(metric -> metric
            .getMetadata()
            .getExpositionBasePrometheusName()
            .equals(name));
    }

    private static MetricsCore core() {
        return new MetricsCore(
            "test",
            "unknown",
            "common",
            false,
            false,
            (collector, failure) -> {}
        );
    }
}
