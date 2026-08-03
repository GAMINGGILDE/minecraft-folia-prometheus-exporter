package de.minecraftgilde.prometheus.folia;

import static de.minecraftgilde.prometheus.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.collector.CollectorState;
import de.minecraftgilde.prometheus.MetricsCore;
import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.HttpConfiguration;
import de.minecraftgilde.prometheus.scheduler.ManualCollectionScheduler;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.time.Clock;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;

class FoliaCollectorTest {

    @Test
    void paperApiDoesNotExposeTheExactRegionTpsCapability() {
        assertFalse(FoliaRegionCapability.isAvailable(Server.class));
        assertTrue(FoliaRegionCapability.isAvailable(FakeFoliaServerApi.class));
    }

    @Test
    void enabledCollectorOnPaperIsUnsupportedWarnsOnceAndLoadsNoProvider()
        throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        List<String> warnings = new ArrayList<>();
        RecordingClassLoader loader = new RecordingClassLoader(
            getClass().getClassLoader()
        );
        FoliaCollector collector = new FoliaCollector(
            true,
            () -> false,
            new ReflectiveFoliaProviderFactory(loader),
            context(registry),
            warnings::add
        );

        collector.start();
        collector.start();

        assertEquals(CollectorState.UNSUPPORTED, collector.state());
        assertEquals(List.of(FoliaCollector.UNSUPPORTED_WARNING), warnings);
        assertEquals(0, loader.providerLoadRequests.get());
        assertTrue(registry.scrape().stream().noneMatch(snapshot -> snapshot
            .getMetadata()
            .getPrometheusName()
            .startsWith("minecraft_folia_")));
    }

    @Test
    void disabledCollectorPerformsNoCapabilityCheckWarningOrRegistration()
        throws Exception {
        AtomicInteger checks = new AtomicInteger();
        AtomicInteger creations = new AtomicInteger();
        List<String> warnings = new ArrayList<>();
        FoliaCollector collector = new FoliaCollector(
            false,
            () -> {
                checks.incrementAndGet();
                return false;
            },
            context -> {
                creations.incrementAndGet();
                return new FakeProvider();
            },
            context(new PrometheusRegistry()),
            warnings::add
        );

        collector.start();
        collector.stop();

        assertEquals(CollectorState.DISABLED, collector.state());
        assertEquals(0, checks.get());
        assertEquals(0, creations.get());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void supportedProviderLifecycleIsCreatedOnceAndIdempotent() throws Exception {
        AtomicInteger creations = new AtomicInteger();
        FakeProvider provider = new FakeProvider();
        FoliaCollector collector = new FoliaCollector(
            true,
            () -> true,
            context -> {
                creations.incrementAndGet();
                return provider;
            },
            context(new PrometheusRegistry()),
            ignored -> {}
        );

        collector.start();
        collector.start();
        collector.stop();
        collector.stop();

        assertEquals(1, creations.get());
        assertEquals(1, provider.starts);
        assertEquals(1, provider.stops);
        assertEquals(CollectorState.STOPPED, collector.state());
    }

    @Test
    void unsupportedOptionalCollectorLeavesHealthAndReadinessAvailable()
        throws Exception {
        try (
            MetricsCore core = new MetricsCore(
                "test",
                "unknown",
                "common",
                false,
                false,
                (collector, failure) -> {}
            )
        ) {
            List<String> warnings = new ArrayList<>();
            core.collectorCoordinator().register(
                new FoliaCollector(
                    true,
                    () -> false,
                    context -> new FakeProvider(),
                    context(core.registry()),
                    warnings::add
                )
            );
            core.start(httpConfiguration(freePort()));

            assertTrue(core.lifecycleState().isHealthy());
            assertTrue(core.lifecycleState().isReady());
            assertEquals(List.of(FoliaCollector.UNSUPPORTED_WARNING), warnings);
            GaugeSnapshot states = (GaugeSnapshot) core.registry()
                .scrape()
                .stream()
                .filter(snapshot -> snapshot.getMetadata()
                    .getPrometheusName()
                    .equals("minecraft_exporter_collector_state"))
                .findFirst()
                .orElseThrow();
            assertTrue(states.getDataPoints().stream().anyMatch(point ->
                "folia".equals(point.getLabels().get("collector"))
                    && "unsupported".equals(point.getLabels().get("state"))
                    && point.getValue() == 1.0
            ));
        }
    }

    private static FoliaProviderContext context(PrometheusRegistry registry) {
        return new FoliaProviderContext(
            registry,
            proxy(Server.class, Map.of()),
            new ManualCollectionScheduler(),
            ExporterConfiguration.defaults(),
            Clock.systemUTC(),
            (collector, failure) -> {}
        );
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static HttpConfiguration httpConfiguration(int port) {
        return new HttpConfiguration(
            "127.0.0.1",
            port,
            "/metrics",
            "/health",
            "/ready",
            1
        );
    }

    private interface FakeFoliaServerApi {

        double[] getRegionTPS(World world, int chunkX, int chunkZ);
    }

    private static final class FakeProvider implements FoliaProvider {

        private int starts;
        private int stops;

        @Override
        public void start() {
            starts++;
        }

        @Override
        public void stop() {
            stops++;
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {

        private final AtomicInteger providerLoadRequests = new AtomicInteger();

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
            if (ReflectiveFoliaProviderFactory.PROVIDER_CLASS_NAME.equals(name)) {
                providerLoadRequests.incrementAndGet();
            }
            return super.loadClass(name, resolve);
        }
    }
}
