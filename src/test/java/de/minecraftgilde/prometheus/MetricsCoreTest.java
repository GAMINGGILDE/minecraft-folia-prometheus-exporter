package de.minecraftgilde.prometheus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.collector.AbstractCollector;
import de.minecraftgilde.prometheus.collector.CollectorState;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.HttpConfiguration;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricsCoreTest {

    @Test
    void optionalCollectorFailureDoesNotBlockReadiness() throws Exception {
        List<String> failures = new ArrayList<>();
        try (
            MetricsCore core = new MetricsCore(
                "test",
                "unknown",
                "common",
                (name, failure) -> failures.add(name)
            )
        ) {
            core.collectorCoordinator().register(
                new AbstractCollector("optional-test", true) {
                    @Override
                    protected void startCollector() {
                        throw new IllegalStateException("expected");
                    }

                    @Override
                    protected void stopCollector() {}
                }
            );

            core.start(
                new HttpConfiguration(
                    "127.0.0.1",
                    0,
                    "/metrics",
                    "/health",
                    "/ready",
                    1
                )
            );

            assertEquals(List.of("optional-test"), failures);
            assertEquals(
                CollectorState.FAILED,
                core.collectorCoordinator().states().get("optional-test")
            );
            assertTrue(core.lifecycleState().isReady());
        }
    }

    @Test
    void cleansUpCollectorsAfterPartialHttpStartupFailure() throws Exception {
        List<String> lifecycle = new ArrayList<>();
        try (
            ServerSocket occupied = new ServerSocket(
                0,
                1,
                InetAddress.getByName("127.0.0.1")
            );
            MetricsCore core = new MetricsCore(
                "test",
                "unknown",
                "common",
                (name, failure) -> {}
            )
        ) {
            core.collectorCoordinator().register(
                new AbstractCollector("required-test", true) {
                    @Override
                    protected void startCollector() {
                        lifecycle.add("start");
                    }

                    @Override
                    protected void stopCollector() {
                        lifecycle.add("stop");
                    }
                }
            );

            assertThrows(
                IOException.class,
                () -> core.start(
                    new HttpConfiguration(
                        "127.0.0.1",
                        occupied.getLocalPort(),
                        "/metrics",
                        "/health",
                        "/ready",
                        1
                    )
                )
            );

            assertEquals(List.of("start", "stop"), lifecycle);
            assertEquals(
                CollectorState.STOPPED,
                core.collectorCoordinator().states().get("required-test")
            );
            assertFalse(core.lifecycleState().isReady());
        }
    }
}
