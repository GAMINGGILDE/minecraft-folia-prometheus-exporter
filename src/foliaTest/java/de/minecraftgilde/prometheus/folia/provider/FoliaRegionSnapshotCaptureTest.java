package de.minecraftgilde.prometheus.folia.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.ObservationSourcesConfiguration;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import de.minecraftgilde.prometheus.scheduler.CollectionTask;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class FoliaRegionSnapshotCaptureTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void deduplicatesPublicAnchorsAndAggregatesPlayersWithoutIdentity() {
        TestServer server = new TestServer();
        RegionObservationRegistry registry = new RegionObservationRegistry();
        registry.start();
        FoliaRegionSnapshotCapture capture = capture(server, registry);
        TestCompletion completion = new TestCompletion();

        capture.capture(completion);

        assertEquals(2, completion.values.size());
        RegionObservation first = completion.values.getFirst();
        RegionObservation second = completion.values.getLast();
        assertEquals(0, first.key().chunkX());
        assertEquals(1, first.players());
        assertEquals(20.0, first.tps().get(TpsWindow.FIVE_SECONDS));
        assertEquals(12, second.key().chunkX());
        assertEquals(2, second.players());
        assertEquals(10.0, second.tps().get(TpsWindow.FIVE_SECONDS));
        assertTrue(completion.failure == null);
    }

    @Test
    void oneRegionFailureKeepsTheLastCompleteRegistryGeneration() {
        TestServer server = new TestServer();
        RegionObservationRegistry registry = new RegionObservationRegistry();
        registry.start();
        FoliaRegionSnapshotCapture capture = capture(server, registry);
        TestCompletion first = new TestCompletion();
        capture.capture(first);
        assertEquals(2, first.values.size());

        server.failRegionB = true;
        TestCompletion failed = new TestCompletion();
        capture.capture(failed);

        assertInstanceOf(IllegalStateException.class, failed.failure);
        assertEquals(
            2,
            registry.current(NOW, Duration.ofSeconds(60)).size()
        );
    }

    @Test
    void invalidNonFiniteApiValueIsNeverPublished() {
        TestServer server = new TestServer();
        server.nonFiniteRegionB = true;
        RegionObservationRegistry registry = new RegionObservationRegistry();
        registry.start();
        TestCompletion completion = new TestCompletion();

        capture(server, registry).capture(completion);

        assertInstanceOf(IllegalArgumentException.class, completion.failure);
        assertTrue(registry.current(NOW, Duration.ofSeconds(60)).isEmpty());
    }

    private static FoliaRegionSnapshotCapture capture(
        TestServer server,
        RegionObservationRegistry registry
    ) {
        ExporterConfiguration configuration = ExporterConfiguration.defaults();
        return new FoliaRegionSnapshotCapture(
            server.server,
            server.scheduler,
            registry,
            CLOCK,
            configuration.folia().observationTtl(),
            new ObservationSourcesConfiguration(true, true, true, List.of()),
            TpsWindow.configured(configuration.folia().tps().windows())
        );
    }

    private static final class TestCompletion
        implements SnapshotCompletion<RegionObservation> {

        private List<RegionObservation> values = List.of();
        private Throwable failure;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final List<Runnable> inactive = new ArrayList<>();

        @Override
        public void success(List<RegionObservation> values) {
            this.values = List.copyOf(values);
            deactivate();
        }

        @Override
        public void failure(Throwable failure) {
            this.failure = failure;
            deactivate();
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void whenInactive(Runnable listener) {
            inactive.add(listener);
        }

        private void deactivate() {
            if (active.compareAndSet(true, false)) {
                inactive.forEach(Runnable::run);
            }
        }
    }

    private static final class TestServer {

        private final ThreadLocal<Integer> currentRegion = new ThreadLocal<>();
        private final World world;
        private final Server server;
        private final ImmediateScheduler scheduler = new ImmediateScheduler();
        private boolean failRegionB;
        private boolean nonFiniteRegionB;

        private TestServer() {
            final World[] worldHolder = new World[1];
            world = proxy(World.class, (method, arguments) -> switch (method) {
                case "getName" -> "world";
                case "getSpawnLocation" -> new Location(worldHolder[0], 0, 64, 0);
                case "getForceLoadedChunks" -> Set.of(
                    proxy(Chunk.class, (chunkMethod, ignored) -> switch (chunkMethod) {
                        case "getX" -> 1;
                        case "getZ" -> 0;
                        case "getWorld" -> worldHolder[0];
                        default -> unsupported(chunkMethod);
                    })
                );
                default -> unsupported(method);
            });
            worldHolder[0] = world;
            List<Player> players = List.of(
                player(new Location(world, 32, 64, 0)),
                player(new Location(world, 192, 64, 0)),
                player(new Location(world, 192, 64, 0))
            );
            server = proxy(Server.class, (method, arguments) -> switch (method) {
                case "getWorlds" -> List.of(world);
                case "getOnlinePlayers" -> players;
                case "isOwnedByCurrentRegion" -> region((int) arguments[1])
                    == currentRegion.get();
                case "getRegionTPS" -> tps(currentRegion.get());
                default -> unsupported(method);
            });
            scheduler.owner = this;
        }

        private Player player(Location location) {
            return proxy(Player.class, (method, arguments) -> switch (method) {
                case "getLocation" -> location.clone();
                default -> unsupported(method);
            });
        }

        private double[] tps(int region) {
            if (region == 1 && failRegionB) {
                throw new IllegalStateException("expected region failure");
            }
            if (region == 1 && nonFiniteRegionB) {
                return new double[] { Double.NaN, 10, 10, 10, 10 };
            }
            double value = region == 0 ? 20.0 : 10.0;
            return new double[] { value, value, value, value, value };
        }

        private static int region(int chunkX) {
            return chunkX < 10 ? 0 : 1;
        }

        private final class ImmediateScheduler implements CollectionScheduler {

            private TestServer owner;

            @Override
            public CollectionTask scheduleGlobalAtFixedRate(
                Duration interval,
                Runnable task
            ) {
                return () -> {};
            }

            @Override
            public CollectionTask executeAt(
                World world,
                int chunkX,
                int chunkZ,
                Runnable task
            ) {
                currentRegion.set(region(chunkX));
                try {
                    task.run();
                } finally {
                    currentRegion.remove();
                }
                return () -> {};
            }

            @Override
            public java.util.Optional<CollectionTask> executeFor(
                Entity entity,
                Runnable task,
                Runnable retired
            ) {
                task.run();
                return java.util.Optional.of(() -> {});
            }

            @Override
            public CollectionTask executeAsync(Runnable task) {
                task.run();
                return () -> {};
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

    @FunctionalInterface
    private interface Invocation {

        Object invoke(String method, Object[] arguments);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] { type },
            (instance, method, arguments) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "TestProxy(" + type.getSimpleName() + ")";
                        case "hashCode" -> System.identityHashCode(instance);
                        case "equals" -> instance == arguments[0];
                        default -> throw new UnsupportedOperationException();
                    };
                }
                return invocation.invoke(
                    method.getName(),
                    arguments == null ? new Object[0] : arguments
                );
            }
        );
    }

    private static Object unsupported(String method) {
        throw new UnsupportedOperationException(method);
    }
}
