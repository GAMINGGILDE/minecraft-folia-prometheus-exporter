package de.minecraftgilde.prometheus.minecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityStateStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");
    private static final UUID ZOMBIE_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID ITEM_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000002"
    );

    private SnapshotRepository<EntityWorldSnapshot> repository;
    private EntityStateStore store;

    @BeforeEach
    void setUp() {
        repository = new SnapshotRepository<>();
        store = new EntityStateStore(
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC),
            true
        );
        store.start();
    }

    @Test
    void eventBeforeObservationIsNotAppliedTwice() {
        long run = store.beginReconciliation().orElseThrow();
        EntityDescriptor zombie = zombie("world");
        store.recordAdd(ZOMBIE_ID, zombie);

        store.commit(
            scan(
                run,
                Set.of("world"),
                List.of(new EntityObservation(
                    ZOMBIE_ID,
                    zombie,
                    store.currentSequence()
                ))
            ),
            NOW
        );

        EntityWorldSnapshot world = world("world");
        assertEquals(1L, world.totalEntities());
        assertEquals(1L, world.group(EntityGroup.MONSTER));
    }

    @Test
    void eventAfterObservationAndImmediatelyBeforeCommitIsAppliedOnce() {
        long run = store.beginReconciliation().orElseThrow();
        EntityDescriptor zombie = zombie("world");
        EntityObservation observed = new EntityObservation(
            ZOMBIE_ID,
            zombie,
            store.currentSequence()
        );
        store.recordAdd(ITEM_ID, item("world"));

        store.commit(
            scan(run, Set.of("world"), List.of(observed)),
            NOW
        );

        EntityWorldSnapshot world = world("world");
        assertEquals(2L, world.totalEntities());
        assertEquals(1L, world.group(EntityGroup.MONSTER));
        assertEquals(1L, world.group(EntityGroup.ITEM));
        assertEquals(
            Map.of("minecraft:item", 1L, "minecraft:zombie", 1L),
            world.exactTypes()
        );
    }

    @Test
    void removalAfterObservationWinsAtCommit() {
        long run = store.beginReconciliation().orElseThrow();
        EntityDescriptor zombie = zombie("world");
        EntityObservation observed = new EntityObservation(
            ZOMBIE_ID,
            zombie,
            store.currentSequence()
        );
        store.recordRemove(ZOMBIE_ID, zombie);

        store.commit(scan(run, Set.of("world"), List.of(observed)), NOW);

        assertEquals(0L, world("world").totalEntities());
    }

    @Test
    void eventsImmediatelyAfterCommitUpdateThePublishedSnapshot() {
        long run = store.beginReconciliation().orElseThrow();
        store.commit(scan(run, Set.of("world"), List.of()), NOW);

        store.recordAdd(ZOMBIE_ID, zombie("world"));

        assertEquals(1L, world("world").totalEntities());
        assertEquals(1L, world("world").livingEntities());
    }

    @Test
    void worldUnloadDuringRunRemovesObservationsAndStaleSeries() {
        long run = store.beginReconciliation().orElseThrow();
        EntityDescriptor zombie = zombie("world");
        EntityObservation observed = new EntityObservation(
            ZOMBIE_ID,
            zombie,
            store.currentSequence()
        );
        store.recordWorldUnload("world");

        store.commit(scan(run, Set.of("world"), List.of(observed)), NOW);

        assertTrue(repository.current().orElseThrow().values().isEmpty());
    }

    @Test
    void retainedWorldKeepsItsLastEventAdjustedValueAfterLocalFailure() {
        long first = store.beginReconciliation().orElseThrow();
        store.commit(
            scan(
                first,
                Set.of("world"),
                List.of(new EntityObservation(
                    ITEM_ID,
                    item("world"),
                    store.currentSequence()
                ))
            ),
            NOW
        );
        store.recordAdd(ZOMBIE_ID, zombie("world"));

        long second = store.beginReconciliation().orElseThrow();
        EntityStateStore.ReconciliationCommit commit = store.commit(
            new EntityScanResult(
                second,
                Map.of("world", EntityWorldScanStatus.PARTIAL),
                List.of(),
                Duration.ofMillis(1)
            ),
            NOW.plusSeconds(1)
        );

        assertEquals(2L, world("world").totalEntities());
        assertEquals(0L, commit.corrections());
    }

    @Test
    void abortedRunRetainsLastSnapshotAndLateCommitCannotReplaceIt() {
        long first = store.beginReconciliation().orElseThrow();
        store.commit(
            scan(
                first,
                Set.of("world"),
                List.of(new EntityObservation(
                    ITEM_ID,
                    item("world"),
                    0L
                ))
            ),
            NOW
        );
        long aborted = store.beginReconciliation().orElseThrow();
        store.abort(aborted);

        assertEquals(1L, world("world").totalEntities());
        assertFalse(store.beginReconciliation().isEmpty());
    }

    @Test
    void successfulEmptyResultRemovesWorldAndExactTypeSeries() {
        long first = store.beginReconciliation().orElseThrow();
        store.commit(
            scan(
                first,
                Set.of("world"),
                List.of(new EntityObservation(
                    ZOMBIE_ID,
                    zombie("world"),
                    0L
                ))
            ),
            NOW
        );
        long second = store.beginReconciliation().orElseThrow();
        store.commit(scan(second, Set.of(), List.of()), NOW.plusSeconds(1));

        assertTrue(repository.current().orElseThrow().values().isEmpty());
    }

    @Test
    void unavailableInitialWorldPublishesNoInventedZeroSeries() {
        long run = store.beginReconciliation().orElseThrow();

        EntityStateStore.ReconciliationCommit commit = store.commit(
            scan(run, Map.of("world", EntityWorldScanStatus.UNAVAILABLE), List.of()),
            NOW
        );

        assertTrue(repository.current().orElseThrow().values().isEmpty());
        assertEquals(0L, commit.corrections());
    }

    @Test
    void partialInitialWorldPublishesNoInventedZeroSeries() {
        long run = store.beginReconciliation().orElseThrow();

        store.commit(
            scan(run, Map.of("world", EntityWorldScanStatus.PARTIAL), List.of()),
            NOW
        );

        assertTrue(repository.current().orElseThrow().values().isEmpty());
    }

    @Test
    void unavailableWorldIsOmittedWhileSuccessfulNeighbourIsPublished() {
        long run = store.beginReconciliation().orElseThrow();

        store.commit(
            scan(
                run,
                Map.of(
                    "broken",
                    EntityWorldScanStatus.UNAVAILABLE,
                    "healthy",
                    EntityWorldScanStatus.SUCCESS
                ),
                List.of(new EntityObservation(
                    ZOMBIE_ID,
                    zombie("healthy"),
                    store.currentSequence()
                ))
            ),
            NOW
        );

        assertEquals(1L, world("healthy").totalEntities());
        assertTrue(repository.current().orElseThrow().values().stream().noneMatch(
            value -> value.world().equals("broken")
        ));
    }

    @Test
    void eventsCannotCreateABaselineForAnUnavailableWorld() {
        long first = store.beginReconciliation().orElseThrow();
        store.commit(
            scan(first, Map.of("healthy", EntityWorldScanStatus.SUCCESS), List.of()),
            NOW
        );

        assertTrue(store.recordAdd(ZOMBIE_ID, zombie("unavailable")));

        assertTrue(repository.current().orElseThrow().values().stream().noneMatch(
            value -> value.world().equals("unavailable")
        ));
    }

    @Test
    void lateCommitFromAbortedRunCannotOverwriteNewerSuccess() {
        long oldRun = store.beginReconciliation().orElseThrow();
        store.abort(oldRun);
        long newRun = store.beginReconciliation().orElseThrow();
        store.commit(
            scan(
                newRun,
                Set.of("world"),
                List.of(new EntityObservation(
                    ZOMBIE_ID,
                    zombie("world"),
                    store.currentSequence()
                ))
            ),
            NOW
        );

        assertThrows(
            IllegalStateException.class,
            () -> store.commit(scan(oldRun, Set.of(), List.of()), NOW.plusSeconds(1))
        );
        assertEquals(1L, world("world").totalEntities());
    }

    @Test
    void reconciliationReportsBoundedCorrectionsAgainstEventDrift() {
        long first = store.beginReconciliation().orElseThrow();
        store.commit(
            scan(
                first,
                Set.of("world"),
                List.of(new EntityObservation(
                    ITEM_ID,
                    item("world"),
                    0L
                ))
            ),
            NOW
        );
        store.recordAdd(ZOMBIE_ID, zombie("world"));

        long second = store.beginReconciliation().orElseThrow();
        EntityStateStore.ReconciliationCommit commit = store.commit(
            scan(
                second,
                Set.of("world"),
                List.of(new EntityObservation(
                    ITEM_ID,
                    item("world"),
                    store.currentSequence()
                ))
            ),
            NOW.plusSeconds(1)
        );

        assertTrue(commit.corrections() >= 3L);
        assertEquals(1L, world("world").totalEntities());
        assertFalse(world("world").exactTypes().containsKey("minecraft:zombie"));
    }

    @Test
    void stopRejectsAllLaterEventUpdates() {
        long run = store.beginReconciliation().orElseThrow();
        store.commit(scan(run, Set.of("world"), List.of()), NOW);
        store.stop();

        assertFalse(store.recordAdd(ZOMBIE_ID, zombie("world")));
        assertFalse(store.recordWorldUnload("world"));
        assertEquals(0L, world("world").totalEntities());
    }

    @Test
    void stopDuringAnActiveRunRejectsItsCommit() {
        long run = store.beginReconciliation().orElseThrow();
        store.stop();

        assertThrows(
            IllegalStateException.class,
            () -> store.commit(scan(run, Set.of("world"), List.of()), NOW)
        );
        assertFalse(repository.hasSnapshot());
    }

    @Test
    void unmatchedRemovalNeverCreatesNegativeCounts() {
        long run = store.beginReconciliation().orElseThrow();
        EntityStateStore.ReconciliationCommit initial = store.commit(
            scan(run, Set.of("world"), List.of()),
            NOW
        );
        assertEquals(0L, initial.corrections());

        assertTrue(store.recordRemove(ZOMBIE_ID, zombie("world")));

        EntityWorldSnapshot value = world("world");
        assertEquals(0L, value.totalEntities());
        assertTrue(value.groups().values().stream().allMatch(count -> count >= 0L));
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

    private static EntityScanResult scan(
        long run,
        Set<String> worlds,
        List<EntityObservation> observations
    ) {
        Map<String, EntityWorldScanStatus> statuses = new java.util.HashMap<>();
        worlds.forEach(world -> statuses.put(
            world,
            EntityWorldScanStatus.SUCCESS
        ));
        return scan(run, statuses, observations);
    }

    private static EntityScanResult scan(
        long run,
        Map<String, EntityWorldScanStatus> worldStatuses,
        List<EntityObservation> observations
    ) {
        return new EntityScanResult(
            run,
            worldStatuses,
            observations,
            Duration.ofMillis(5)
        );
    }

    private static EntityDescriptor zombie(String world) {
        return new EntityDescriptor(
            world,
            EntityGroup.MONSTER,
            "minecraft:zombie",
            true
        );
    }

    private static EntityDescriptor item(String world) {
        return new EntityDescriptor(
            world,
            EntityGroup.ITEM,
            "minecraft:item",
            false
        );
    }
}
