package de.minecraftgilde.prometheus.minecraft.entity;

import static de.minecraftgilde.prometheus.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import de.minecraftgilde.prometheus.collector.PeriodicSnapshotCollector;
import de.minecraftgilde.prometheus.scheduler.ManualCollectionScheduler;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.jupiter.api.Test;

class EntityCollectorTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-04T12:00:00Z"),
        ZoneOffset.UTC
    );

    @Test
    void addAndRemoveEventsUpdateExactlyOnceAndPlayersStayExcluded()
        throws Exception {
        Fixture fixture = new Fixture(failure -> {});
        fixture.collector.start();
        fixture.initialize("world");
        World world = world("world");
        Entity zombie = living(EntityType.ZOMBIE, 1L);
        Entity player = player(2L);

        fixture.collector.onEntityAdded(new EntityAddToWorldEvent(zombie, world));
        fixture.collector.onEntityAdded(new EntityAddToWorldEvent(player, world));
        assertEquals(1L, fixture.world("world").totalEntities());

        fixture.collector.onEntityRemoved(
            new EntityRemoveFromWorldEvent(zombie, world)
        );
        assertEquals(0L, fixture.world("world").totalEntities());
        fixture.collector.stop();
        assertEquals(1, fixture.registration.registered.get());
        assertEquals(1, fixture.registration.unregistered.get());
    }

    @Test
    void cancelledWorldUnloadIsIgnoredAndCompletedUnloadRemovesTheWorld()
        throws Exception {
        Fixture fixture = new Fixture(failure -> {});
        fixture.collector.start();
        fixture.initialize("world");
        World world = world("world");
        WorldUnloadEvent unload = new WorldUnloadEvent(world);
        unload.setCancelled(true);

        fixture.collector.onWorldUnload(unload);
        assertEquals(1, fixture.repository.current().orElseThrow().values().size());

        unload.setCancelled(false);
        fixture.collector.onWorldUnload(unload);
        assertEquals(0, fixture.repository.current().orElseThrow().values().size());
        fixture.collector.stop();
    }

    @Test
    void stopBlocksLaterUpdatesAndIsIdempotent() throws Exception {
        Fixture fixture = new Fixture(failure -> {});
        fixture.collector.start();
        fixture.initialize("world");
        fixture.collector.stop();
        fixture.collector.stop();

        fixture.collector.onEntityAdded(
            new EntityAddToWorldEvent(living(EntityType.ZOMBIE, 3L), world("world"))
        );

        assertEquals(0L, fixture.world("world").totalEntities());
        assertEquals(1, fixture.registration.unregistered.get());
    }

    @Test
    void reporterFailureNeverEscapesTheEventThread() throws Exception {
        Fixture fixture = new Fixture(failure -> {
            throw new IllegalStateException("reporter failure");
        });
        fixture.collector.start();
        fixture.initialize("world");
        Entity broken = proxy(
            Entity.class,
            Map.of(
                "getType",
                (java.util.function.Function<Object[], Object>) ignored -> {
                    throw new IllegalStateException("entity failure");
                }
            )
        );

        assertDoesNotThrow(() -> fixture.collector.onEntityAdded(
            new EntityAddToWorldEvent(broken, world("world"))
        ));
        fixture.collector.stop();
    }

    @Test
    void unregisterFailureStillClosesTheEventAndStateBoundaries()
        throws Exception {
        Fixture fixture = new Fixture(failure -> {});
        fixture.collector.start();
        fixture.initialize("world");
        fixture.registration.failOnUnregister = true;

        assertThrows(IllegalStateException.class, fixture.collector::stop);
        fixture.collector.onEntityAdded(
            new EntityAddToWorldEvent(living(EntityType.ZOMBIE, 7L), world("world"))
        );

        assertEquals(0L, fixture.world("world").totalEntities());
        assertEquals(1, fixture.registration.unregistered.get());
    }

    @Test
    void worldTransferMovesOneEntityBetweenSourceAndTarget() throws Exception {
        Fixture fixture = new Fixture(failure -> {});
        fixture.collector.start();
        fixture.initialize("source", "target");
        Entity zombie = living(EntityType.ZOMBIE, 4L);
        fixture.collector.onEntityAdded(
            new EntityAddToWorldEvent(zombie, world("source"))
        );

        fixture.collector.onEntityRemoved(
            new EntityRemoveFromWorldEvent(zombie, world("source"))
        );
        fixture.collector.onEntityAdded(
            new EntityAddToWorldEvent(zombie, world("target"))
        );

        assertEquals(0L, fixture.world("source").totalEntities());
        assertEquals(1L, fixture.world("target").totalEntities());
        fixture.collector.stop();
    }

    @Test
    void transformationRemovesOldTypeAndAddsEveryReplacementOnce()
        throws Exception {
        Fixture fixture = new Fixture(failure -> {});
        fixture.collector.start();
        fixture.initialize("world");
        Entity zombie = living(EntityType.ZOMBIE, 5L);
        Entity villager = living(EntityType.VILLAGER, 6L);
        World world = world("world");
        fixture.collector.onEntityAdded(new EntityAddToWorldEvent(zombie, world));

        fixture.collector.onEntityRemoved(
            new EntityRemoveFromWorldEvent(zombie, world)
        );
        fixture.collector.onEntityAdded(
            new EntityAddToWorldEvent(villager, world)
        );

        assertEquals(0L, fixture.world("world").group(EntityGroup.MONSTER));
        assertEquals(1L, fixture.world("world").group(EntityGroup.VILLAGER));
        assertEquals(
            Map.of("minecraft:villager", 1L),
            fixture.world("world").exactTypes()
        );
        fixture.collector.stop();
    }

    private static World world(String name) {
        return proxy(World.class, Map.of("getName", name));
    }

    private static Entity living(EntityType type, long suffix) {
        return proxy(
            LivingEntity.class,
            Map.of(
                "getType",
                type,
                "getUniqueId",
                new UUID(0L, suffix)
            )
        );
    }

    private static Entity player(long suffix) {
        return proxy(
            Player.class,
            Map.of(
                "getType",
                EntityType.PLAYER,
                "getUniqueId",
                new UUID(0L, suffix)
            )
        );
    }

    private static final class Fixture {

        private final SnapshotRepository<EntityWorldSnapshot> repository =
            new SnapshotRepository<>();
        private final EntityStateStore store = new EntityStateStore(
            repository,
            CLOCK,
            true
        );
        private final ManualCollectionScheduler scheduler =
            new ManualCollectionScheduler();
        private final TestRegistration registration = new TestRegistration();
        private final EntityCollector collector;

        private Fixture(java.util.function.Consumer<Throwable> failureListener) {
            PeriodicSnapshotCollector<EntityScanResult> reconciliation =
                new PeriodicSnapshotCollector<>(
                    "entity-reconciliation",
                    true,
                    scheduler,
                    Duration.ofMinutes(5),
                    Duration.ofMinutes(1),
                    completion -> {},
                    (capturedAt, values) -> {},
                    CLOCK,
                    (name, failure) -> {}
                );
            collector = new EntityCollector(
                true,
                registration,
                store,
                new EntityGroupClassifier(),
                reconciliation,
                failureListener
            );
        }

        private void initialize(String... worlds) {
            long run = store.beginReconciliation().orElseThrow();
            store.commit(
                new EntityScanResult(
                    run,
                    Set.of(worlds),
                    Set.of(),
                    List.of(),
                    Duration.ZERO
                ),
                CLOCK.instant()
            );
        }

        private EntityWorldSnapshot world(String name) {
            return repository.current()
                .orElseThrow()
                .values()
                .stream()
                .filter(value -> value.world().equals(name))
                .findFirst()
                .orElseThrow();
        }
    }

    private static final class TestRegistration
        implements EntityEventRegistration {

        private final AtomicInteger registered = new AtomicInteger();
        private final AtomicInteger unregistered = new AtomicInteger();
        private boolean failOnUnregister;

        @Override
        public void register(Listener listener) {
            registered.incrementAndGet();
        }

        @Override
        public void unregister(Listener listener) {
            unregistered.incrementAndGet();
            if (failOnUnregister) {
                throw new IllegalStateException("expected unregister failure");
            }
        }
    }
}
