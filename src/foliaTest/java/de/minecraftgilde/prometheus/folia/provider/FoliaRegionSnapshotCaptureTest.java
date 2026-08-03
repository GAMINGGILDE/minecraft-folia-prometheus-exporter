package de.minecraftgilde.prometheus.folia.provider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
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
    private static final ObservationSourcesConfiguration ALL_SOURCES =
        new ObservationSourcesConfiguration(true, true, true, List.of());
    private static final ObservationSourcesConfiguration PLAYERS_ONLY =
        new ObservationSourcesConfiguration(true, false, false, List.of());
    private static final ObservationSourcesConfiguration FORCE_LOADED_ONLY =
        new ObservationSourcesConfiguration(false, false, true, List.of());
    private static final ObservationSourcesConfiguration NO_SOURCES =
        new ObservationSourcesConfiguration(false, false, false, List.of());

    @Test
    void deduplicatesPublicAnchorsAndAggregatesPlayersWithoutIdentity() {
        TestServer server = new TestServer();
        server.regionOf = chunkX -> chunkX < 10 ? 0 : 1;
        server.forceLoadedChunks.add(1);
        server.players.add(server.playerAt(2));
        server.players.add(server.playerAt(12));
        server.players.add(server.playerAt(12));
        server.tps = region -> values(region == 0 ? 20.0 : 10.0);
        RegionObservationRegistry registry = startedRegistry();
        TestCompletion completion = new TestCompletion();

        capture(server, registry, ALL_SOURCES, (key, failure) -> {})
            .capture(completion);

        assertEquals(1, completion.successes.get());
        assertEquals(0, completion.failures.get());
        assertEquals(2, completion.values.size());
        RegionObservation first = completion.values.getFirst();
        RegionObservation second = completion.values.getLast();
        assertEquals(0, first.key().chunkX());
        assertEquals(1, first.players());
        assertEquals(20.0, first.tps().get(TpsWindow.FIVE_SECONDS));
        assertEquals(12, second.key().chunkX());
        assertEquals(2, second.players());
        assertEquals(10.0, second.tps().get(TpsWindow.FIVE_SECONDS));
    }

    @Test
    void playerFailureAndDuplicateRetireCallbackDoNotAbortOrDoubleComplete() {
        TestServer server = new TestServer();
        server.players.add(server.playerAt(0));
        server.players.add(server.failingPlayer(
            new IllegalStateException("player Alice UUID at chunk 12 region 34")
        ));
        server.scheduler.invokeRetiredAfterPlayerCallback = true;
        List<ReportedFailure> reported = new CopyOnWriteArrayList<>();
        RegionObservationRegistry registry = startedRegistry();
        TestCompletion completion = new TestCompletion();

        capture(server, registry, PLAYERS_ONLY, (key, failure) -> reported.add(
            new ReportedFailure(key, failure)
        )).capture(completion);

        assertEquals(1, completion.successes.get());
        assertEquals(0, completion.failures.get());
        assertEquals(1, completion.values.size());
        assertEquals(1, completion.values.getFirst().players());
        assertEquals(1, server.scheduler.regionTasks.size());
        assertEquals(1, reported.size());
        assertEquals(
            FoliaRegionSnapshotCapture.PLAYER_FAILURE_MESSAGE,
            reported.getFirst().failure().getMessage()
        );
        assertFalse(reported.getFirst().failure().getMessage().contains("Alice"));
    }

    @Test
    void playerSchedulingRejectionSkipsOnlyThatAnchor() {
        TestServer server = new TestServer();
        server.players.add(server.playerAt(0));
        server.players.add(server.playerAt(1));
        server.scheduler.rejectedPlayerTasks.add(1);
        List<ReportedFailure> reported = new ArrayList<>();
        TestCompletion completion = new TestCompletion();

        capture(
            server,
            startedRegistry(),
            PLAYERS_ONLY,
            (key, failure) -> reported.add(new ReportedFailure(key, failure))
        ).capture(completion);

        assertEquals(1, completion.successes.get());
        assertEquals(1, completion.values.size());
        assertEquals(1, reported.size());
    }

    @Test
    void invalidAndFailingRegionsAreSkippedWhileValidRegionsArePublished() {
        TestServer server = new TestServer();
        server.forceLoadedChunks.addAll(
            List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        );
        server.tps = region -> switch (region) {
            case 0 -> values(20.0);
            case 1 -> null;
            case 2 -> new double[] { 20.0, 20.0, 20.0, 20.0 };
            case 3 -> values(Double.NaN);
            case 4 -> throw new IllegalStateException(
                "region 4 at chunk 4 must stay private"
            );
            case 5 -> values(15.0);
            case 8 -> values(-1.0);
            case 9 -> values(10_001.0);
            default -> values(10.0);
        };
        server.scheduler.rejectedRegionTasks.add(6);
        server.unownedChunks.add(7);
        List<ReportedFailure> reported = new CopyOnWriteArrayList<>();
        TestCompletion completion = new TestCompletion();

        capture(
            server,
            startedRegistry(),
            FORCE_LOADED_ONLY,
            (key, failure) -> reported.add(new ReportedFailure(key, failure))
        ).capture(completion);

        assertEquals(1, completion.successes.get());
        assertEquals(0, completion.failures.get());
        assertEquals(List.of(0, 5), completion.values
            .stream()
            .map(value -> value.key().chunkX())
            .toList());
        assertEquals(8, reported.size());
        assertTrue(reported.stream().allMatch(value -> value
            .failure()
            .getMessage()
            .equals(FoliaRegionSnapshotCapture.REGION_FAILURE_MESSAGE)));
        assertEquals(0, server.scheduler.cancelledRegionTasks.get());
    }

    @Test
    void successfulEmptyRunReplacesThePreviousRegistryGeneration() {
        TestServer server = new TestServer();
        server.worldsAvailable = false;
        RegionObservationRegistry registry = startedRegistry();
        commit(registry, observation(99));
        TestCompletion completion = new TestCompletion();

        capture(server, registry, NO_SOURCES, (key, failure) -> {})
            .capture(completion);

        assertEquals(1, completion.successes.get());
        assertTrue(completion.values.isEmpty());
        assertTrue(registry.current(NOW, Duration.ofMinutes(1)).isEmpty());
    }

    @Test
    void allLocallyInvalidRegionsStillReplaceThePreviousGenerationWithEmpty() {
        TestServer server = new TestServer();
        server.forceLoadedChunks.add(0);
        server.tps = ignored -> null;
        RegionObservationRegistry registry = startedRegistry();
        commit(registry, observation(99));
        TestCompletion completion = new TestCompletion();

        capture(server, registry, FORCE_LOADED_ONLY, (key, failure) -> {})
            .capture(completion);

        assertEquals(1, completion.successes.get());
        assertEquals(0, completion.failures.get());
        assertTrue(completion.values.isEmpty());
        assertTrue(registry.current(NOW, Duration.ofMinutes(1)).isEmpty());
    }

    @Test
    void globalWorldListFailureIsSystemicAndRetainsThePreviousGeneration() {
        TestServer server = new TestServer();
        server.failWorldList = true;
        RegionObservationRegistry registry = startedRegistry();
        commit(registry, observation(99));
        TestCompletion completion = new TestCompletion();

        capture(server, registry, NO_SOURCES, (key, failure) -> {})
            .capture(completion);

        assertEquals(0, completion.successes.get());
        assertEquals(1, completion.failures.get());
        assertInstanceOf(IllegalStateException.class, completion.failure);
        assertEquals(
            List.of(99),
            registry.current(NOW, Duration.ofMinutes(1))
                .stream()
                .map(value -> value.key().chunkX())
                .toList()
        );
    }

    @Test
    void abortCancelsPendingTasksAndLateCallbacksCannotPublish() {
        TestServer server = new TestServer();
        server.forceLoadedChunks.addAll(List.of(0, 1));
        server.scheduler.queueRegionTasks = true;
        RegionObservationRegistry registry = startedRegistry();
        commit(registry, observation(99));
        TestCompletion completion = new TestCompletion();

        capture(server, registry, FORCE_LOADED_ONLY, (key, failure) -> {})
            .capture(completion);
        assertEquals(2, server.scheduler.regionTasks.size());

        completion.deactivate();
        server.scheduler.invokeAllRegionCallbacksLate();

        assertEquals(2, server.scheduler.cancelledRegionTasks.get());
        assertEquals(0, completion.successes.get());
        assertEquals(0, completion.failures.get());
        assertEquals(
            List.of(99),
            registry.current(NOW, Duration.ofMinutes(1))
                .stream()
                .map(value -> value.key().chunkX())
                .toList()
        );
    }

    @Test
    void concurrentLocalFailureAndLastSuccessCompleteExactlyOnce()
        throws Exception {
        TestServer server = new TestServer();
        server.forceLoadedChunks.addAll(List.of(0, 1));
        server.scheduler.queueRegionTasks = true;
        CyclicBarrier barrier = new CyclicBarrier(2);
        server.tps = region -> {
            await(barrier);
            if (region == 1) {
                throw new IllegalStateException("expected local failure");
            }
            return values(20.0);
        };
        List<ReportedFailure> reported = new CopyOnWriteArrayList<>();
        RegionObservationRegistry registry = startedRegistry();
        TestCompletion completion = new TestCompletion();
        capture(
            server,
            registry,
            FORCE_LOADED_ONLY,
            (key, failure) -> reported.add(new ReportedFailure(key, failure))
        ).capture(completion);

        CountDownLatch finished = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (TestRegionTask task : server.scheduler.regionTasks) {
                executor.execute(() -> {
                    try {
                        task.run();
                    } finally {
                        finished.countDown();
                    }
                });
            }
            assertTrue(finished.await(5, TimeUnit.SECONDS));
        }

        assertEquals(1, completion.successes.get());
        assertEquals(0, completion.failures.get());
        assertEquals(1, completion.values.size());
        assertEquals(1, reported.size());

        server.scheduler.invokeAllRegionCallbacksLate();
        assertEquals(1, completion.successes.get());
        assertEquals(0, completion.failures.get());
        assertEquals(1, registry.current(NOW, Duration.ofMinutes(1)).size());
    }

    @Test
    void throwingFailureReporterCannotEscapeARegionThreadOrLeakDetails() {
        TestServer server = new TestServer();
        server.forceLoadedChunks.add(42);
        server.tps = ignored -> throwFailure(
            new IllegalStateException(
                "player Alice UUID 01234567-89ab-cdef-0123-456789abcdef chunk 42 region 7"
            )
        );
        List<Throwable> reported = new ArrayList<>();
        TestCompletion completion = new TestCompletion();

        assertDoesNotThrow(() -> capture(
            server,
            startedRegistry(),
            FORCE_LOADED_ONLY,
            (key, failure) -> {
                reported.add(failure);
                throw new IllegalStateException("reporter failed");
            }
        ).capture(completion));

        assertEquals(1, completion.successes.get());
        assertTrue(completion.values.isEmpty());
        assertEquals(1, reported.size());
        Throwable sanitized = reported.getFirst();
        assertEquals(
            FoliaRegionSnapshotCapture.REGION_FAILURE_MESSAGE,
            sanitized.getMessage()
        );
        assertNull(sanitized.getCause());
        assertFalse(sanitized.getMessage().contains("Alice"));
        assertFalse(sanitized.getMessage().contains("42"));
        assertFalse(sanitized.getMessage().contains("region 7"));
    }

    private static FoliaRegionSnapshotCapture capture(
        TestServer server,
        RegionObservationRegistry registry,
        ObservationSourcesConfiguration sources,
        BiConsumer<String, Throwable> reporter
    ) {
        ExporterConfiguration configuration = ExporterConfiguration.defaults();
        return new FoliaRegionSnapshotCapture(
            server.server,
            server.scheduler,
            registry,
            CLOCK,
            configuration.folia().observationTtl(),
            sources,
            TpsWindow.configured(configuration.folia().tps().windows()),
            reporter
        );
    }

    private static RegionObservationRegistry startedRegistry() {
        RegionObservationRegistry registry = new RegionObservationRegistry();
        registry.start();
        return registry;
    }

    private static void commit(
        RegionObservationRegistry registry,
        RegionObservation observation
    ) {
        long run = registry.beginRun().orElseThrow();
        assertTrue(registry.update(run, observation));
        registry.completeRun(run).orElseThrow();
    }

    private static RegionObservation observation(int chunkX) {
        return new RegionObservation(
            new RegionObservationKey("world", chunkX, 0),
            NOW,
            Map.of(TpsWindow.FIVE_SECONDS, 20.0),
            0
        );
    }

    private static double[] values(double value) {
        return new double[] { value, value, value, value, value };
    }

    private static double[] throwFailure(RuntimeException failure) {
        throw failure;
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("Barrier failed", failure);
        }
    }

    private static final class TestCompletion
        implements SnapshotCompletion<RegionObservation> {

        private final AtomicInteger successes = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final Object listenerLock = new Object();
        private final List<Runnable> inactive = new ArrayList<>();
        private volatile List<RegionObservation> values = List.of();
        private volatile Throwable failure;

        @Override
        public void success(List<RegionObservation> values) {
            successes.incrementAndGet();
            this.values = List.copyOf(values);
            deactivate();
        }

        @Override
        public void failure(Throwable failure) {
            failures.incrementAndGet();
            this.failure = failure;
            deactivate();
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void whenInactive(Runnable listener) {
            boolean runImmediately;
            synchronized (listenerLock) {
                runImmediately = !active.get();
                if (!runImmediately) {
                    inactive.add(listener);
                }
            }
            if (runImmediately) {
                listener.run();
            }
        }

        private void deactivate() {
            List<Runnable> listeners;
            synchronized (listenerLock) {
                if (!active.compareAndSet(true, false)) {
                    return;
                }
                listeners = List.copyOf(inactive);
                inactive.clear();
            }
            listeners.forEach(Runnable::run);
        }
    }

    private static final class TestServer {

        private final ThreadLocal<Integer> currentRegion = new ThreadLocal<>();
        private final List<Integer> forceLoadedChunks = new ArrayList<>();
        private final Set<Integer> unownedChunks = ConcurrentHashMap.newKeySet();
        private final List<Player> players = new ArrayList<>();
        private final World world;
        private final Server server;
        private final TestScheduler scheduler = new TestScheduler();
        private IntUnaryOperator regionOf = value -> value;
        private IntFunction<double[]> tps = ignored -> values(20.0);
        private boolean worldsAvailable = true;
        private boolean failWorldList;

        private TestServer() {
            final World[] worldHolder = new World[1];
            world = proxy(World.class, (method, arguments) -> switch (method) {
                case "getName" -> "world";
                case "getSpawnLocation" -> new Location(
                    worldHolder[0],
                    0,
                    64,
                    0
                );
                case "getForceLoadedChunks" -> chunks(worldHolder[0]);
                default -> unsupported(method);
            });
            worldHolder[0] = world;
            server = proxy(Server.class, (method, arguments) -> switch (method) {
                case "getWorlds" -> worlds();
                case "getOnlinePlayers" -> List.copyOf(players);
                case "isOwnedByCurrentRegion" -> !unownedChunks.contains(
                    (int) arguments[1]
                ) && regionOf.applyAsInt((int) arguments[1])
                    == currentRegion.get();
                case "getRegionTPS" -> tps.apply(currentRegion.get());
                default -> unsupported(method);
            });
        }

        private List<World> worlds() {
            if (failWorldList) {
                throw new IllegalStateException("global world list failed");
            }
            return worldsAvailable ? List.of(world) : List.of();
        }

        private Set<Chunk> chunks(World chunkWorld) {
            Set<Chunk> result = new LinkedHashSet<>();
            for (int chunkX : forceLoadedChunks) {
                result.add(proxy(Chunk.class, (method, arguments) -> switch (method) {
                    case "getX" -> chunkX;
                    case "getZ" -> 0;
                    case "getWorld" -> chunkWorld;
                    default -> unsupported(method);
                }));
            }
            return result;
        }

        private Player playerAt(int chunkX) {
            return proxy(Player.class, (method, arguments) -> switch (method) {
                case "getLocation" -> new Location(world, chunkX << 4, 64, 0);
                default -> unsupported(method);
            });
        }

        private Player failingPlayer(RuntimeException failure) {
            return proxy(Player.class, (method, arguments) -> switch (method) {
                case "getLocation" -> throw failure;
                default -> unsupported(method);
            });
        }

        private final class TestScheduler implements CollectionScheduler {

            private final List<TestRegionTask> regionTasks =
                new CopyOnWriteArrayList<>();
            private final Set<Integer> rejectedRegionTasks =
                ConcurrentHashMap.newKeySet();
            private final Set<Integer> rejectedPlayerTasks =
                ConcurrentHashMap.newKeySet();
            private final AtomicInteger cancelledRegionTasks =
                new AtomicInteger();
            private final AtomicInteger playerTaskIndex = new AtomicInteger();
            private boolean queueRegionTasks;
            private boolean invokeRetiredAfterPlayerCallback;

            @Override
            public CollectionTask scheduleGlobalAtFixedRate(
                Duration interval,
                Runnable task
            ) {
                return () -> {};
            }

            @Override
            public CollectionTask executeAt(
                World taskWorld,
                int chunkX,
                int chunkZ,
                Runnable task
            ) {
                if (rejectedRegionTasks.contains(chunkX)) {
                    throw new IllegalStateException(
                        "region scheduler rejected task"
                    );
                }
                TestRegionTask scheduled = new TestRegionTask(
                    chunkX,
                    task,
                    cancelledRegionTasks,
                    currentRegion,
                    regionOf
                );
                regionTasks.add(scheduled);
                if (!queueRegionTasks) {
                    scheduled.run();
                }
                return scheduled;
            }

            @Override
            public Optional<CollectionTask> executeFor(
                Entity entity,
                Runnable task,
                Runnable retired
            ) {
                int index = playerTaskIndex.getAndIncrement();
                if (rejectedPlayerTasks.contains(index)) {
                    throw new IllegalStateException(
                        "player scheduler rejected task"
                    );
                }
                task.run();
                if (invokeRetiredAfterPlayerCallback) {
                    retired.run();
                }
                return Optional.of(() -> {});
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

            private void invokeAllRegionCallbacksLate() {
                regionTasks.forEach(TestRegionTask::invokeLate);
            }
        }
    }

    private static final class TestRegionTask implements CollectionTask {

        private final int chunkX;
        private final Runnable callback;
        private final AtomicInteger cancellationCount;
        private final ThreadLocal<Integer> currentRegion;
        private final IntUnaryOperator regionOf;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();

        private TestRegionTask(
            int chunkX,
            Runnable callback,
            AtomicInteger cancellationCount,
            ThreadLocal<Integer> currentRegion,
            IntUnaryOperator regionOf
        ) {
            this.chunkX = chunkX;
            this.callback = callback;
            this.cancellationCount = cancellationCount;
            this.currentRegion = currentRegion;
            this.regionOf = regionOf;
        }

        private void run() {
            if (
                cancelled.get()
                    || !completed.compareAndSet(false, true)
            ) {
                return;
            }
            invoke();
        }

        private void invokeLate() {
            invoke();
        }

        private void invoke() {
            currentRegion.set(regionOf.applyAsInt(chunkX));
            try {
                callback.run();
            } finally {
                currentRegion.remove();
            }
        }

        @Override
        public void cancel() {
            if (!completed.get() && cancelled.compareAndSet(false, true)) {
                cancellationCount.incrementAndGet();
            }
        }
    }

    private record ReportedFailure(String key, Throwable failure) {}

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
