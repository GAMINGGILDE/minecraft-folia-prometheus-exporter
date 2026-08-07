package de.minecraftgilde.prometheus.minecraft.entity;

import static de.minecraftgilde.prometheus.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.collector.PeriodicSnapshotCollector;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import de.minecraftgilde.prometheus.scheduler.CollectionTask;
import de.minecraftgilde.prometheus.scheduler.ManualCollectionScheduler;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class BukkitEntityReconciliationCaptureTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-04T14:00:00Z"),
        ZoneOffset.UTC
    );

    @Test
    void distributesChunkAndEntityReadsAndExcludesPlayers() {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        World[] worldHolder = new World[1];
        Entity zombie = living(EntityType.ZOMBIE, 1L, worldHolder);
        Entity player = player(2L, worldHolder);
        Chunk[] chunkHolder = new Chunk[1];
        World world = proxy(
            World.class,
            Map.of(
                "getName",
                "world",
                "getLoadedChunks",
                (java.util.function.Function<Object[], Object>) ignored ->
                    new Chunk[] { chunkHolder[0] }
            )
        );
        worldHolder[0] = world;
        chunkHolder[0] = chunk(world, new Entity[] { zombie, player });
        EntityStateStore store = store();
        RecordingCompletion completion = new RecordingCompletion();

        capture(server(List.of(world)), scheduler, store, (key, failure) -> {})
            .capture(completion);

        assertNull(completion.failure);
        EntityScanResult result = completion.values.getFirst();
        assertEquals(
            Map.of("world", EntityWorldScanStatus.SUCCESS),
            result.worldStatuses()
        );
        assertEquals(1, result.observations().size());
        assertEquals(EntityGroup.MONSTER, result.observations()
            .getFirst()
            .descriptor()
            .group());
        assertEquals(2, scheduler.entityExecutions());
    }

    @Test
    void noReliablyCapturedWorldFailsTheRunWithoutPublishingAnEmptyResult() {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        World[] holder = new World[1];
        Chunk broken = proxy(
            Chunk.class,
            Map.ofEntries(
                Map.entry("getX", 0),
                Map.entry("getZ", 0),
                Map.entry(
                    "getWorld",
                    (java.util.function.Function<Object[], Object>) ignored ->
                        holder[0]
                ),
                Map.entry("isLoaded", true),
                Map.entry("isEntitiesLoaded", true),
                Map.entry(
                    "getEntities",
                    (java.util.function.Function<Object[], Object>) ignored -> {
                        throw new IllegalStateException("expected");
                    }
                )
            )
        );
        World world = proxy(
            World.class,
            Map.of("getName", "world", "getLoadedChunks", new Chunk[] { broken })
        );
        holder[0] = world;
        RecordingCompletion completion = new RecordingCompletion();
        java.util.concurrent.atomic.AtomicInteger failures =
            new java.util.concurrent.atomic.AtomicInteger();

        capture(
            server(List.of(world)),
            scheduler,
            store(),
            (key, failure) -> failures.incrementAndGet()
        ).capture(completion);

        assertNotNull(completion.failure);
        assertNull(completion.values);
        assertEquals(1, failures.get());
    }

    @Test
    void unavailableWorldIsReportedButSuccessfulEmptyNeighbourStillCompletes() {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        IllegalStateException original = new IllegalStateException(
            "private uuid 00000000-0000-0000-0000-000000000123 at 12,64,9"
        );
        World broken = proxy(
            World.class,
            Map.of(
                "getName",
                "broken",
                "getLoadedChunks",
                (java.util.function.Function<Object[], Object>) ignored -> {
                    throw original;
                }
            )
        );
        World healthy = proxy(
            World.class,
            Map.of("getName", "healthy", "getLoadedChunks", new Chunk[0])
        );
        RecordingCompletion completion = new RecordingCompletion();
        List<Throwable> failures = new java.util.ArrayList<>();

        capture(
            server(List.of(broken, healthy)),
            scheduler,
            store(),
            (key, failure) -> failures.add(failure)
        ).capture(completion);

        assertNull(completion.failure);
        assertEquals(
            Map.of(
                "broken",
                EntityWorldScanStatus.UNAVAILABLE,
                "healthy",
                EntityWorldScanStatus.SUCCESS
            ),
            completion.values.getFirst().worldStatuses()
        );
        assertEquals(1, failures.size());
        assertEquals(
            BukkitEntityReconciliationCapture.WORLD_FAILURE_MESSAGE,
            failures.getFirst().getMessage()
        );
        assertSame(original, failures.getFirst().getCause());
        assertTrue(!failures.getFirst().getMessage().contains("00000000"));
        assertTrue(!failures.getFirst().getMessage().contains("12,64,9"));
    }

    @Test
    void successfullyEnumeratedEmptyWorldIsReliable() {
        RecordingCompletion completion = new RecordingCompletion();
        World world = proxy(
            World.class,
            Map.of("getName", "world", "getLoadedChunks", new Chunk[0])
        );

        capture(
            server(List.of(world)),
            new ManualCollectionScheduler(),
            store(),
            (key, failure) -> {}
        ).capture(completion);

        assertNull(completion.failure);
        assertEquals(
            Map.of("world", EntityWorldScanStatus.SUCCESS),
            completion.values.getFirst().worldStatuses()
        );
        assertTrue(completion.values.getFirst().observations().isEmpty());
    }

    @Test
    void reporterFailureDoesNotEscapeARegionOrEntityScheduler() {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        World[] holder = new World[1];
        Entity broken = proxy(
            Entity.class,
            Map.ofEntries(
                Map.entry(
                    "getWorld",
                    (java.util.function.Function<Object[], Object>) ignored ->
                        holder[0]
                ),
                Map.entry(
                    "getType",
                    (java.util.function.Function<Object[], Object>) ignored -> {
                        throw new IllegalStateException("expected");
                    }
                )
            )
        );
        World world = proxy(
            World.class,
            Map.of("getName", "world", "getLoadedChunks", new Chunk[0])
        );
        holder[0] = world;
        RecordingCompletion completion = new RecordingCompletion();

        Chunk chunk = chunk(world, new Entity[] { broken });
        World worldWithChunk = proxy(
            World.class,
            Map.of("getName", "world", "getLoadedChunks", new Chunk[] { chunk })
        );
        World healthy = proxy(
            World.class,
            Map.of("getName", "healthy", "getLoadedChunks", new Chunk[0])
        );
        capture(
            server(List.of(worldWithChunk, healthy)),
            scheduler,
            store(),
            (key, failure) -> {
                throw new IllegalStateException("reporter failure");
            }
        ).capture(completion);

        assertNull(completion.failure);
        assertNotNull(completion.values);
        assertTrue(completion.values.getFirst().observations().isEmpty());
    }

    @Test
    void globalWorldListFailureIsSystemic() {
        IllegalStateException original = new IllegalStateException(
            "world-list technical details"
        );
        Server server = proxy(
            Server.class,
            Map.of(
                "getWorlds",
                (java.util.function.Function<Object[], Object>) ignored -> {
                    throw original;
                }
            )
        );
        RecordingCompletion completion = new RecordingCompletion();

        capture(
            server,
            new ManualCollectionScheduler(),
            store(),
            (key, failure) -> {}
        ).capture(completion);

        assertNotNull(completion.failure);
        assertNull(completion.values);
        assertEquals(
            "Entity reconciliation failed systemically.",
            completion.failure.getMessage()
        );
        assertSame(original, completion.failure.getCause());
    }

    @Test
    void commitCallbackFailureRetainsItsCauseBehindANeutralMessage() {
        IllegalArgumentException original = new IllegalArgumentException(
            "private commit details"
        );
        AtomicReference<Throwable> reported = new AtomicReference<>();
        SnapshotCompletion<EntityScanResult> completion =
            new SnapshotCompletion<>() {
                @Override
                public void success(List<EntityScanResult> values) {
                    throw original;
                }

                @Override
                public boolean successIfActive(
                    java.util.function.Supplier<List<EntityScanResult>> values
                ) {
                    throw original;
                }

                @Override
                public void failure(Throwable failure) {
                    reported.set(failure);
                }
            };
        World world = proxy(
            World.class,
            Map.of("getName", "world", "getLoadedChunks", new Chunk[0])
        );

        capture(
            server(List.of(world)),
            new ManualCollectionScheduler(),
            store(),
            (key, failure) -> {}
        ).capture(completion);

        assertEquals(
            "Entity reconciliation commit failed.",
            reported.get().getMessage()
        );
        assertSame(original, reported.get().getCause());
    }

    @Test
    void timeoutDuringPendingChunkRejectsTheLateCallbackAndAllowsANewerRun()
        throws Exception {
        DeferredScheduler scheduler = new DeferredScheduler();
        AtomicReference<Chunk[]> chunks = new AtomicReference<>();
        World world = proxy(
            World.class,
            Map.of(
                "getName",
                "world",
                "getLoadedChunks",
                (java.util.function.Function<Object[], Object>) ignored ->
                    chunks.get()
            )
        );
        Chunk chunk = proxy(
            Chunk.class,
            Map.of(
                "getX",
                0,
                "getZ",
                0,
                "getWorld",
                world,
                "isLoaded",
                true,
                "isEntitiesLoaded",
                true,
                "getEntities",
                new Entity[0]
            )
        );
        chunks.set(new Chunk[] { chunk });
        SnapshotRepository<EntityWorldSnapshot> repository =
            new SnapshotRepository<>();
        EntityStateStore stateStore = new EntityStateStore(
            repository,
            CLOCK,
            false
        );
        stateStore.start();
        List<Throwable> failures = new ArrayList<>();
        BukkitEntityReconciliationCapture capture = capture(
            server(List.of(world)),
            scheduler,
            stateStore,
            (key, failure) -> failures.add(failure)
        );
        PeriodicSnapshotCollector<EntityScanResult> collector =
            reconciliation(scheduler, stateStore, capture, failures);
        collector.start();

        scheduler.runGlobal();
        assertEquals(1, scheduler.pendingRegions());
        scheduler.runDelayed();
        assertTrue(!repository.hasSnapshot());

        chunks.set(new Chunk[0]);
        scheduler.runGlobal();
        assertEquals(10, repository.current().orElseThrow()
            .values()
            .getFirst()
            .groups()
            .size());
        scheduler.runRegions();
        assertEquals(0L, repository.current().orElseThrow()
            .values()
            .getFirst()
            .totalEntities());
        collector.stop();
    }

    @Test
    void timeoutDuringPendingEntityRejectsTheRetiredAndLateCallbacks()
        throws Exception {
        DeferredScheduler scheduler = new DeferredScheduler();
        AtomicReference<Chunk[]> chunks = new AtomicReference<>();
        World[] worldHolder = new World[1];
        Entity zombie = living(EntityType.ZOMBIE, 44L, worldHolder);
        World world = proxy(
            World.class,
            Map.of(
                "getName",
                "world",
                "getLoadedChunks",
                (java.util.function.Function<Object[], Object>) ignored ->
                    chunks.get()
            )
        );
        worldHolder[0] = world;
        chunks.set(new Chunk[] { chunk(world, new Entity[] { zombie }) });
        SnapshotRepository<EntityWorldSnapshot> repository =
            new SnapshotRepository<>();
        EntityStateStore stateStore = new EntityStateStore(
            repository,
            CLOCK,
            false
        );
        stateStore.start();
        List<Throwable> failures = new ArrayList<>();
        BukkitEntityReconciliationCapture capture = capture(
            server(List.of(world)),
            scheduler,
            stateStore,
            (key, failure) -> failures.add(failure)
        );
        PeriodicSnapshotCollector<EntityScanResult> collector =
            reconciliation(scheduler, stateStore, capture, failures);
        collector.start();

        scheduler.runGlobal();
        scheduler.runRegions();
        assertEquals(1, scheduler.pendingEntities());
        scheduler.runDelayed();
        chunks.set(new Chunk[0]);
        scheduler.runGlobal();
        scheduler.retireEntities();
        scheduler.runEntities();

        assertEquals(0L, repository.current().orElseThrow()
            .values()
            .getFirst()
            .totalEntities());
        collector.stop();
    }

    @Test
    void entitySchedulerRetireMakesOnlyItsWorldPartial() {
        DeferredScheduler scheduler = new DeferredScheduler();
        World[] holder = new World[1];
        Entity zombie = living(EntityType.ZOMBIE, 51L, holder);
        World broken = proxy(
            World.class,
            Map.of("getName", "broken", "getLoadedChunks", new Chunk[0])
        );
        holder[0] = broken;
        Chunk chunk = chunk(broken, new Entity[] { zombie });
        broken = proxy(
            World.class,
            Map.of("getName", "broken", "getLoadedChunks", new Chunk[] { chunk })
        );
        holder[0] = broken;
        World healthy = proxy(
            World.class,
            Map.of("getName", "healthy", "getLoadedChunks", new Chunk[0])
        );
        RecordingCompletion completion = new RecordingCompletion();
        List<Throwable> failures = new ArrayList<>();

        capture(
            server(List.of(broken, healthy)),
            scheduler,
            store(),
            (key, failure) -> failures.add(failure)
        ).capture(completion);
        scheduler.runRegions();
        scheduler.retireEntities();

        assertNull(completion.failure);
        assertEquals(
            EntityWorldScanStatus.PARTIAL,
            completion.values.getFirst().worldStatuses().get("broken")
        );
        assertEquals(
            EntityWorldScanStatus.SUCCESS,
            completion.values.getFirst().worldStatuses().get("healthy")
        );
        assertEquals(1, failures.size());
        assertEquals(
            BukkitEntityReconciliationCapture.ENTITY_FAILURE_MESSAGE,
            failures.getFirst().getMessage()
        );
        assertEquals(IllegalStateException.class, failures.getFirst()
            .getCause()
            .getClass());
    }

    private static BukkitEntityReconciliationCapture capture(
        Server server,
        CollectionScheduler scheduler,
        EntityStateStore store,
        java.util.function.BiConsumer<String, Throwable> failures
    ) {
        AtomicLong nanos = new AtomicLong();
        return new BukkitEntityReconciliationCapture(
            server,
            scheduler,
            store,
            new EntityGroupClassifier(),
            () -> nanos.getAndAdd(1_000_000L),
            failures
        );
    }

    private static EntityStateStore store() {
        EntityStateStore store = new EntityStateStore(
            new SnapshotRepository<>(),
            CLOCK,
            true
        );
        store.start();
        return store;
    }

    private static PeriodicSnapshotCollector<EntityScanResult> reconciliation(
        DeferredScheduler scheduler,
        EntityStateStore stateStore,
        BukkitEntityReconciliationCapture capture,
        List<Throwable> failures
    ) {
        return new PeriodicSnapshotCollector<>(
            "entity-reconciliation",
            true,
            scheduler,
            Duration.ofMinutes(1),
            Duration.ofSeconds(1),
            capture,
            (capturedAt, values) -> stateStore.commit(
                values.getFirst(),
                capturedAt
            ),
            CLOCK,
            (key, failure) -> failures.add(failure)
        );
    }

    private static Server server(List<World> worlds) {
        return proxy(Server.class, Map.of("getWorlds", worlds));
    }

    private static Chunk chunk(World world, Entity[] entities) {
        return proxy(
            Chunk.class,
            Map.ofEntries(
                Map.entry("getX", 0),
                Map.entry("getZ", 0),
                Map.entry("getWorld", world),
                Map.entry("isLoaded", true),
                Map.entry("isEntitiesLoaded", true),
                Map.entry("getEntities", entities)
            )
        );
    }

    private static Entity living(
        EntityType type,
        long suffix,
        World[] holder
    ) {
        return proxy(
            LivingEntity.class,
            Map.of(
                "getWorld",
                (java.util.function.Function<Object[], Object>) ignored -> holder[0],
                "getType",
                type,
                "getUniqueId",
                new UUID(0L, suffix)
            )
        );
    }

    private static Entity player(long suffix, World[] holder) {
        return proxy(
            Player.class,
            Map.of(
                "getWorld",
                (java.util.function.Function<Object[], Object>) ignored -> holder[0],
                "getType",
                EntityType.PLAYER,
                "getUniqueId",
                new UUID(0L, suffix)
            )
        );
    }

    private static final class RecordingCompletion
        implements SnapshotCompletion<EntityScanResult> {

        private List<EntityScanResult> values;
        private Throwable failure;

        @Override
        public void success(List<EntityScanResult> values) {
            this.values = List.copyOf(values);
        }

        @Override
        public void failure(Throwable failure) {
            this.failure = failure;
        }
    }

    private static final class DeferredScheduler implements CollectionScheduler {

        private final List<DeferredTask> globals = new ArrayList<>();
        private final List<DeferredTask> regions = new ArrayList<>();
        private final List<DeferredTask> entities = new ArrayList<>();
        private final List<DeferredTask> delayed = new ArrayList<>();

        @Override
        public CollectionTask scheduleGlobalAtFixedRate(
            Duration interval,
            Runnable task
        ) {
            DeferredTask result = new DeferredTask(task, null, true);
            globals.add(result);
            return result;
        }

        @Override
        public CollectionTask executeAt(
            World world,
            int chunkX,
            int chunkZ,
            Runnable task
        ) {
            DeferredTask result = new DeferredTask(task, null, false);
            regions.add(result);
            return result;
        }

        @Override
        public Optional<CollectionTask> executeFor(
            Entity entity,
            Runnable task,
            Runnable retired
        ) {
            DeferredTask result = new DeferredTask(task, retired, false);
            entities.add(result);
            return Optional.of(result);
        }

        @Override
        public CollectionTask executeAsync(Runnable task) {
            DeferredTask result = new DeferredTask(task, null, false);
            result.run();
            return result;
        }

        @Override
        public CollectionTask executeAsyncAfter(Duration delay, Runnable task) {
            DeferredTask result = new DeferredTask(task, null, false);
            delayed.add(result);
            return result;
        }

        @Override
        public void cancelAll() {
            java.util.stream.Stream.of(globals, regions, entities, delayed)
                .flatMap(List::stream)
                .forEach(DeferredTask::cancel);
        }

        private void runGlobal() {
            globals.forEach(DeferredTask::run);
        }

        private void runRegions() {
            regions.forEach(DeferredTask::run);
        }

        private void runEntities() {
            entities.forEach(DeferredTask::run);
        }

        private void retireEntities() {
            entities.forEach(DeferredTask::retire);
        }

        private void runDelayed() {
            delayed.forEach(DeferredTask::run);
        }

        private int pendingRegions() {
            return (int) regions.stream().filter(DeferredTask::pending).count();
        }

        private int pendingEntities() {
            return (int) entities.stream().filter(DeferredTask::pending).count();
        }
    }

    private static final class DeferredTask implements CollectionTask {

        private final Runnable task;
        private final Runnable retired;
        private final boolean repeating;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();

        private DeferredTask(
            Runnable task,
            Runnable retired,
            boolean repeating
        ) {
            this.task = task;
            this.retired = retired;
            this.repeating = repeating;
        }

        private void run() {
            if (
                cancelled.get()
                    || (!repeating && !completed.compareAndSet(false, true))
            ) {
                return;
            }
            task.run();
        }

        private void retire() {
            if (
                retired != null
                    && !cancelled.get()
                    && completed.compareAndSet(false, true)
            ) {
                retired.run();
            }
        }

        private boolean pending() {
            return !cancelled.get() && !completed.get();
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }
}
