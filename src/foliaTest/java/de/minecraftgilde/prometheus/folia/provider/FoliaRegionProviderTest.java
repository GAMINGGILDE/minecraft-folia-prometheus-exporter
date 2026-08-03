package de.minecraftgilde.prometheus.folia.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.folia.FoliaProviderContext;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import de.minecraftgilde.prometheus.scheduler.CollectionTask;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

class FoliaRegionProviderTest {

    @Test
    void startStopAndRegistrationAreIdempotent() throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        CountingScheduler scheduler = new CountingScheduler();
        Server server = (Server) Proxy.newProxyInstance(
            Server.class.getClassLoader(),
            new Class<?>[] { Server.class },
            (instance, method, arguments) -> {
                throw new UnsupportedOperationException(method.getName());
            }
        );
        FoliaRegionProvider provider = new FoliaRegionProvider(
            new FoliaProviderContext(
                registry,
                server,
                scheduler,
                ExporterConfiguration.defaults(),
                Clock.systemUTC(),
                (collector, failure) -> {}
            )
        );

        provider.start();
        provider.start();
        provider.stop();
        provider.stop();

        assertEquals(1, scheduler.periodicTasks.get());
        assertEquals(1, scheduler.cancelledTasks.get());
        assertTrue(registry.scrape().stream().anyMatch(snapshot -> snapshot
            .getMetadata()
            .getPrometheusName()
            .equals("minecraft_folia_region_tps")));
    }

    @Test
    void runtimeFailureReportingSuppressesAnchorAndPlayerDetails() {
        IllegalStateException original = new IllegalStateException(
            "player Alice at chunk 12,34 in region 56"
        );

        Throwable sanitized = FoliaRegionProvider.sanitizeFailure(original);

        assertEquals(
            "A Folia region observation run failed; anchor details were suppressed.",
            sanitized.getMessage()
        );
        assertNull(sanitized.getCause());
        assertTrue(!sanitized.getMessage().contains("Alice"));
        assertTrue(!sanitized.getMessage().contains("12,34"));
    }

    private static final class CountingScheduler implements CollectionScheduler {

        private final AtomicInteger periodicTasks = new AtomicInteger();
        private final AtomicInteger cancelledTasks = new AtomicInteger();

        @Override
        public CollectionTask scheduleGlobalAtFixedRate(
            Duration interval,
            Runnable task
        ) {
            periodicTasks.incrementAndGet();
            return cancelledTasks::incrementAndGet;
        }

        @Override
        public CollectionTask executeAt(
            World world,
            int chunkX,
            int chunkZ,
            Runnable task
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CollectionTask> executeFor(
            Entity entity,
            Runnable task,
            Runnable retired
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CollectionTask executeAsync(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CollectionTask executeAsyncAfter(
            Duration delay,
            Runnable task
        ) {
            return () -> {};
        }

        @Override
        public void cancelAll() {}
    }
}
