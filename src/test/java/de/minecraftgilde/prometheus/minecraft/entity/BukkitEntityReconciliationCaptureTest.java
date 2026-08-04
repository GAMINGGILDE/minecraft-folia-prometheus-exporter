package de.minecraftgilde.prometheus.minecraft.entity;

import static de.minecraftgilde.prometheus.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.scheduler.ManualCollectionScheduler;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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
        assertEquals(java.util.Set.of("world"), result.loadedWorlds());
        assertEquals(1, result.observations().size());
        assertEquals(EntityGroup.MONSTER, result.observations()
            .getFirst()
            .descriptor()
            .group());
        assertEquals(2, scheduler.entityExecutions());
    }

    @Test
    void chunkFailureIsLocalAndRetainsAWorldWhenNoChunkSucceeded() {
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

        assertNull(completion.failure);
        assertEquals(java.util.Set.of("world"), completion.values
            .getFirst()
            .retainedWorlds());
        assertEquals(1, failures.get());
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
        capture(
            server(List.of(worldWithChunk)),
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
        Server server = proxy(
            Server.class,
            Map.of(
                "getWorlds",
                (java.util.function.Function<Object[], Object>) ignored -> {
                    throw new IllegalStateException("expected");
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
    }

    private static BukkitEntityReconciliationCapture capture(
        Server server,
        ManualCollectionScheduler scheduler,
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
}
