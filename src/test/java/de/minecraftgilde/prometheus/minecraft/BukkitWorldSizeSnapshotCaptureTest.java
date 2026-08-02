package de.minecraftgilde.prometheus.minecraft;

import static de.minecraftgilde.prometheus.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.scheduler.ManualCollectionScheduler;
import de.minecraftgilde.prometheus.snapshot.ImmutableSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BukkitWorldSizeSnapshotCaptureTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void concurrencyOneStartsWorldsSequentiallyInDeterministicOrder() {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler(false);
        RecordingCalculator calculator = new RecordingCalculator();
        TestCompletion completion = new TestCompletion();
        BukkitWorldSizeSnapshotCapture capture = capture(
            List.of(world("zeta"), world("alpha"), world("middle")),
            scheduler,
            calculator,
            new SnapshotRepository<>(),
            failure -> {},
            1
        );

        capture.capture(completion);

        assertEquals(1, capture.activeScans());
        assertEquals(2, capture.queuedScans());
        assertEquals(1, scheduler.queuedAsyncTasks());
        scheduler.runNextAsync();
        assertEquals(List.of("alpha"), calculator.startedWorlds());
        assertEquals(1, scheduler.queuedAsyncTasks());
        assertEquals(1, capture.activeScans());

        scheduler.runNextAsync();
        scheduler.runNextAsync();

        assertEquals(List.of("alpha", "middle", "zeta"), calculator.startedWorlds());
        assertEquals(
            List.of(
                new WorldSizeSnapshot("alpha", 1),
                new WorldSizeSnapshot("middle", 1),
                new WorldSizeSnapshot("zeta", 1)
            ),
            completion.values.get()
        );
        assertEquals(0, capture.activeScans());
    }

    @Test
    void concurrencyTwoNeverReservesMoreThanTwoSlotsAndThirdWaits() {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler(false);
        BukkitWorldSizeSnapshotCapture capture = capture(
            List.of(world("one"), world("two"), world("three")),
            scheduler,
            new RecordingCalculator(),
            new SnapshotRepository<>(),
            failure -> {},
            2
        );
        TestCompletion completion = new TestCompletion();

        capture.capture(completion);

        assertEquals(2, capture.activeScans());
        assertEquals(1, capture.queuedScans());
        assertEquals(2, scheduler.queuedAsyncTasks());
        scheduler.runNextAsync();
        assertEquals(2, capture.activeScans());
        assertEquals(0, capture.queuedScans());
        assertEquals(2, scheduler.queuedAsyncTasks());
        assertNull(completion.values.get());

        scheduler.runNextAsync();
        scheduler.runNextAsync();

        assertEquals(0, capture.activeScans());
        assertEquals(3, completion.values.get().size());
    }

    @Test
    void observedCalculatorConcurrencyNeverExceedsConfiguredLimit()
        throws Exception {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler(false);
        BlockingCalculator calculator = new BlockingCalculator(2);
        BukkitWorldSizeSnapshotCapture capture = capture(
            List.of(world("one"), world("two"), world("three")),
            scheduler,
            calculator,
            new SnapshotRepository<>(),
            failure -> {},
            2
        );
        TestCompletion completion = new TestCompletion();
        capture.capture(completion);

        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<?> first = workers.submit(scheduler::runNextAsync);
            Future<?> second = workers.submit(scheduler::runNextAsync);
            assertTrue(calculator.awaitInitialStarts());
            assertEquals(2, calculator.maximumActive());

            calculator.releaseAll();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            workers.submit(scheduler::runNextAsync).get(5, TimeUnit.SECONDS);
        }

        assertEquals(2, calculator.maximumActive());
        assertEquals(3, completion.values.get().size());
    }

    @Test
    void calculationFailureReleasesSlotAndRetainsOnlyAffectedWorld() {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler(false);
        RecordingCalculator calculator = new RecordingCalculator(Set.of("alpha"));
        SnapshotRepository<WorldSizeSnapshot> repository = repository(
            new WorldSizeSnapshot("alpha", 42)
        );
        List<Throwable> failures = new ArrayList<>();
        BukkitWorldSizeSnapshotCapture capture = capture(
            List.of(world("beta"), world("alpha")),
            scheduler,
            calculator,
            repository,
            failures::add,
            1
        );
        TestCompletion completion = new TestCompletion();

        capture.capture(completion);
        scheduler.runNextAsync();

        assertEquals(1, failures.size());
        assertEquals(1, scheduler.queuedAsyncTasks());
        scheduler.runNextAsync();
        assertEquals(
            List.of(
                new WorldSizeSnapshot("alpha", 42),
                new WorldSizeSnapshot("beta", 1)
            ),
            completion.values.get()
        );
    }

    @Test
    void schedulingFailureReleasesSlotAndStartsNextWorld() {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler(false);
        scheduler.failNextAsyncExecutions(1);
        List<Throwable> failures = new ArrayList<>();
        BukkitWorldSizeSnapshotCapture capture = capture(
            List.of(world("alpha"), world("beta")),
            scheduler,
            new RecordingCalculator(),
            new SnapshotRepository<>(),
            failures::add,
            1
        );
        TestCompletion completion = new TestCompletion();

        capture.capture(completion);

        assertEquals(1, failures.size());
        assertEquals(1, scheduler.queuedAsyncTasks());
        scheduler.runNextAsync();
        assertEquals(List.of(new WorldSizeSnapshot("beta", 1)), completion.values.get());
        assertEquals(0, capture.activeScans());
    }

    @Test
    void sameDirectoryIsNeverScannedConcurrently() {
        Path shared = temporaryDirectory.resolve("shared");
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler(false);
        RecordingCalculator calculator = new RecordingCalculator();
        BukkitWorldSizeSnapshotCapture capture = capture(
            List.of(world("alpha", shared), world("beta", shared)),
            scheduler,
            calculator,
            new SnapshotRepository<>(),
            failure -> {},
            2
        );

        capture.capture(new TestCompletion());

        assertEquals(1, capture.activeScans());
        assertEquals(1, capture.queuedScans());
        scheduler.runNextAsync();
        assertEquals(1, scheduler.queuedAsyncTasks());
        scheduler.runNextAsync();
        assertEquals(1, calculator.maximumActive());
    }

    @Test
    void independentCaptureInstancesDoNotShareConcurrencyState() {
        Path shared = temporaryDirectory.resolve("shared");
        ManualCollectionScheduler firstScheduler = new ManualCollectionScheduler(false);
        ManualCollectionScheduler secondScheduler = new ManualCollectionScheduler(false);
        BukkitWorldSizeSnapshotCapture first = capture(
            List.of(world("world", shared)),
            firstScheduler,
            new RecordingCalculator(),
            new SnapshotRepository<>(),
            failure -> {},
            1
        );
        BukkitWorldSizeSnapshotCapture second = capture(
            List.of(world("world", shared)),
            secondScheduler,
            new RecordingCalculator(),
            new SnapshotRepository<>(),
            failure -> {},
            1
        );

        first.capture(new TestCompletion());
        second.capture(new TestCompletion());

        assertEquals(1, first.calculationsInProgress());
        assertEquals(1, second.calculationsInProgress());
        assertEquals(1, firstScheduler.queuedAsyncTasks());
        assertEquals(1, secondScheduler.queuedAsyncTasks());
    }

    @Test
    void timeoutCancelsScheduledAndQueuedScansWithoutPublishing() {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler(false);
        RecordingCalculator calculator = new RecordingCalculator();
        BukkitWorldSizeSnapshotCapture capture = capture(
            List.of(world("one"), world("two"), world("three")),
            scheduler,
            calculator,
            new SnapshotRepository<>(),
            failure -> {},
            1
        );
        TestCompletion completion = new TestCompletion();

        capture.capture(completion);
        completion.invalidate();

        assertEquals(0, capture.activeScans());
        assertEquals(0, capture.queuedScans());
        assertEquals(0, scheduler.queuedAsyncTasks());
        scheduler.runAsync();
        assertTrue(calculator.startedWorlds().isEmpty());
        assertNull(completion.values.get());
    }

    @Test
    void timedOutRunningScanCannotPublishAndNewRunWaitsForSamePath()
        throws Exception {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler(false);
        BlockingCalculator calculator = new BlockingCalculator(1);
        Path path = temporaryDirectory.resolve("world");
        BukkitWorldSizeSnapshotCapture capture = capture(
            List.of(world("world", path)),
            scheduler,
            calculator,
            new SnapshotRepository<>(),
            failure -> {},
            1
        );
        TestCompletion oldRun = new TestCompletion();
        capture.capture(oldRun);

        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Future<?> oldScan = worker.submit(scheduler::runNextAsync);
            assertTrue(calculator.awaitInitialStarts());
            oldRun.invalidate();

            TestCompletion newRun = new TestCompletion();
            capture.capture(newRun);
            assertEquals(1, capture.queuedScans());
            assertEquals(0, scheduler.queuedAsyncTasks());

            calculator.releaseAll();
            oldScan.get(5, TimeUnit.SECONDS);
            assertNull(oldRun.values.get());
            assertEquals(1, scheduler.queuedAsyncTasks());
            worker.submit(scheduler::runNextAsync).get(5, TimeUnit.SECONDS);
            assertEquals(
                List.of(new WorldSizeSnapshot("world", 1)),
                newRun.values.get()
            );
        }
    }

    @Test
    void closeIsIdempotentAndRunningScanStartsNoQueuedWorkOrCompletion()
        throws Exception {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler(false);
        BlockingCalculator calculator = new BlockingCalculator(1);
        BukkitWorldSizeSnapshotCapture capture = capture(
            List.of(world("one"), world("two")),
            scheduler,
            calculator,
            new SnapshotRepository<>(),
            failure -> {},
            1
        );
        TestCompletion completion = new TestCompletion();
        capture.capture(completion);

        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Future<?> running = worker.submit(scheduler::runNextAsync);
            assertTrue(calculator.awaitInitialStarts());
            capture.close();
            capture.close();
            calculator.releaseAll();
            running.get(5, TimeUnit.SECONDS);
        }

        assertEquals(List.of("one"), calculator.startedWorlds());
        assertEquals(0, capture.queuedScans());
        assertEquals(0, capture.activeScans());
        assertNull(completion.values.get());
        capture.capture(new TestCompletion());
        assertEquals(0, scheduler.queuedAsyncTasks());
    }

    @Test
    void preservesLastValueOnFailureAndDropsRemovedWorlds() {
        AtomicReference<List<World>> worlds = new AtomicReference<>(
            List.of(world("world", temporaryDirectory.resolve("missing")))
        );
        Server server = proxy(
            Server.class,
            Map.of("getWorlds", (java.util.function.Function<Object[], Object>) ignored -> worlds.get())
        );
        SnapshotRepository<WorldSizeSnapshot> repository = repository(
            new WorldSizeSnapshot("world", 42)
        );
        List<Throwable> failures = new ArrayList<>();
        BukkitWorldSizeSnapshotCapture capture = new BukkitWorldSizeSnapshotCapture(
            server,
            new ManualCollectionScheduler(),
            new WorldSizeCalculator(),
            repository,
            failures::add,
            1
        );

        TestCompletion failed = new TestCompletion();
        capture.capture(failed);
        assertEquals(List.of(new WorldSizeSnapshot("world", 42)), failed.values.get());
        assertEquals(1, failures.size());

        worlds.set(List.of());
        TestCompletion removed = new TestCompletion();
        capture.capture(removed);
        assertTrue(removed.values.get().isEmpty());
        assertNull(removed.failure.get());
    }

    @Test
    void aWorldPathFailureDoesNotBlockOtherWorlds() throws Exception {
        Path healthyPath = Files.createDirectory(
            temporaryDirectory.resolve("healthy")
        );
        Files.write(healthyPath.resolve("level.dat"), new byte[9]);
        World broken = proxy(
            World.class,
            Map.of(
                "getName",
                "broken",
                "getWorldPath",
                (java.util.function.Function<Object[], Object>) ignored -> {
                    throw new IllegalStateException("expected");
                }
            )
        );
        SnapshotRepository<WorldSizeSnapshot> repository = repository(
            new WorldSizeSnapshot("broken", 42)
        );
        List<Throwable> failures = new ArrayList<>();
        BukkitWorldSizeSnapshotCapture capture = new BukkitWorldSizeSnapshotCapture(
            server(List.of(broken, world("healthy", healthyPath))),
            new ManualCollectionScheduler(),
            new WorldSizeCalculator(),
            repository,
            failures::add,
            1
        );

        TestCompletion completion = new TestCompletion();
        capture.capture(completion);

        assertEquals(1, failures.size());
        assertEquals(
            List.of(
                new WorldSizeSnapshot("broken", 42),
                new WorldSizeSnapshot("healthy", 9)
            ),
            completion.values.get()
        );
    }

    private BukkitWorldSizeSnapshotCapture capture(
        List<World> worlds,
        ManualCollectionScheduler scheduler,
        WorldSizeCalculation calculator,
        SnapshotRepository<WorldSizeSnapshot> repository,
        java.util.function.Consumer<Throwable> failures,
        int concurrency
    ) {
        return new BukkitWorldSizeSnapshotCapture(
            server(worlds),
            scheduler,
            calculator,
            repository,
            failures,
            concurrency
        );
    }

    private World world(String name) {
        return world(name, temporaryDirectory.resolve(name));
    }

    private static Server server(List<World> worlds) {
        return proxy(Server.class, Map.of("getWorlds", worlds));
    }

    private static World world(String name, Path path) {
        return proxy(
            World.class,
            Map.of("getName", name, "getWorldPath", path)
        );
    }

    private static SnapshotRepository<WorldSizeSnapshot> repository(
        WorldSizeSnapshot... values
    ) {
        SnapshotRepository<WorldSizeSnapshot> repository = new SnapshotRepository<>();
        repository.publish(new ImmutableSnapshot<>(Instant.EPOCH, List.of(values)));
        return repository;
    }

    private static class RecordingCalculator implements WorldSizeCalculation {

        private final Set<String> failures;
        protected final List<String> started = new CopyOnWriteArrayList<>();
        protected final AtomicInteger active = new AtomicInteger();
        protected final AtomicInteger maximumActive = new AtomicInteger();

        private RecordingCalculator() {
            this(Set.of());
        }

        private RecordingCalculator(Set<String> failures) {
            this.failures = new HashSet<>(failures);
        }

        @Override
        public WorldSizeCalculator.Result calculate(Path worldDirectory)
            throws IOException {
            String world = worldDirectory.getFileName().toString();
            started.add(world);
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            try {
                if (failures.contains(world)) {
                    throw new IOException("expected failure");
                }
                return new WorldSizeCalculator.Result(1, 0);
            } finally {
                active.decrementAndGet();
            }
        }

        protected final List<String> startedWorlds() {
            return List.copyOf(started);
        }

        protected final int maximumActive() {
            return maximumActive.get();
        }
    }

    private static final class BlockingCalculator extends RecordingCalculator {

        private final CountDownLatch initialStarts;
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingCalculator(int expectedInitialStarts) {
            initialStarts = new CountDownLatch(expectedInitialStarts);
        }

        @Override
        public WorldSizeCalculator.Result calculate(Path worldDirectory)
            throws IOException {
            String world = worldDirectory.getFileName().toString();
            started.add(world);
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            initialStarts.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("test scan was not released");
                }
                return new WorldSizeCalculator.Result(1, 0);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("test scan interrupted", interrupted);
            } finally {
                active.decrementAndGet();
            }
        }

        private boolean awaitInitialStarts() throws InterruptedException {
            return initialStarts.await(5, TimeUnit.SECONDS);
        }

        private void releaseAll() {
            release.countDown();
        }
    }

    private static final class TestCompletion
        implements SnapshotCompletion<WorldSizeSnapshot> {

        private final AtomicReference<List<WorldSizeSnapshot>> values =
            new AtomicReference<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final List<Runnable> inactiveListeners = new CopyOnWriteArrayList<>();

        @Override
        public void success(List<WorldSizeSnapshot> result) {
            if (active.compareAndSet(true, false)) {
                values.set(result);
                inactiveListeners.forEach(Runnable::run);
            }
        }

        @Override
        public void failure(Throwable result) {
            if (active.compareAndSet(true, false)) {
                failure.set(result);
                inactiveListeners.forEach(Runnable::run);
            }
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void whenInactive(Runnable listener) {
            inactiveListeners.add(listener);
            if (!active.get() && inactiveListeners.remove(listener)) {
                listener.run();
            }
        }

        private void invalidate() {
            if (active.compareAndSet(true, false)) {
                inactiveListeners.forEach(Runnable::run);
            }
        }
    }
}
