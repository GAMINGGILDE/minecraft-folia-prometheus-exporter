package de.minecraftgilde.prometheus.minecraft.entity;

import de.minecraftgilde.prometheus.snapshot.ImmutableSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Serializes event updates with reconciliation commits and publishes immutable state.
 */
final class EntityStateStore {

    private final SnapshotRepository<EntityWorldSnapshot> repository;
    private final Clock clock;
    private final boolean includeExactTypes;
    private final Object lock = new Object();
    private Map<String, EntityWorldSnapshot> current = Map.of();
    private ReconciliationJournal activeRun;
    private boolean accepting;
    private boolean initialized;
    private long sequence;
    private long nextRunId;

    EntityStateStore(
        SnapshotRepository<EntityWorldSnapshot> repository,
        Clock clock,
        boolean includeExactTypes
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.includeExactTypes = includeExactTypes;
    }

    void start() {
        synchronized (lock) {
            accepting = true;
        }
    }

    void stop() {
        synchronized (lock) {
            accepting = false;
            activeRun = null;
        }
    }

    OptionalLong beginReconciliation() {
        synchronized (lock) {
            if (!accepting || activeRun != null) {
                return OptionalLong.empty();
            }
            long runId = Math.incrementExact(nextRunId);
            activeRun = new ReconciliationJournal(runId);
            return OptionalLong.of(runId);
        }
    }

    long currentSequence() {
        synchronized (lock) {
            return sequence;
        }
    }

    boolean recordAdd(UUID identity, EntityDescriptor descriptor) {
        return recordEntity(
            EntityStateDelta.Kind.ADD,
            identity,
            descriptor
        );
    }

    boolean recordRemove(UUID identity, EntityDescriptor descriptor) {
        return recordEntity(
            EntityStateDelta.Kind.REMOVE,
            identity,
            descriptor
        );
    }

    boolean recordWorldLoad(String world) {
        return recordWorld(EntityStateDelta.Kind.WORLD_LOAD, world);
    }

    boolean recordWorldUnload(String world) {
        return recordWorld(EntityStateDelta.Kind.WORLD_UNLOAD, world);
    }

    ReconciliationCommit commit(EntityScanResult scan, Instant capturedAt) {
        Objects.requireNonNull(scan, "scan");
        Objects.requireNonNull(capturedAt, "capturedAt");
        synchronized (lock) {
            ReconciliationJournal journal = activeRun;
            if (
                !accepting
                    || journal == null
                    || journal.runId != scan.runId()
            ) {
                throw new IllegalStateException(
                    "Entity reconciliation run is no longer active"
                );
            }

            Map<UUID, ObservedState> entities = observationsByIdentity(
                scan.observations()
            );
            Set<String> loadedWorlds = new HashSet<>(scan.loadedWorlds());
            Set<String> retainedWorlds = new HashSet<>(scan.retainedWorlds());
            replay(
                journal.deltas,
                entities,
                loadedWorlds,
                retainedWorlds
            );

            Map<String, MutableWorld> aggregates = new HashMap<>();
            loadedWorlds.forEach(world -> aggregates.put(
                world,
                new MutableWorld(world)
            ));
            for (ObservedState state : entities.values()) {
                EntityDescriptor descriptor = state.descriptor;
                MutableWorld world = aggregates.get(descriptor.world());
                if (world != null) {
                    world.add(descriptor, includeExactTypes);
                }
            }

            Map<String, EntityWorldSnapshot> reconciled = new LinkedHashMap<>();
            for (String world : new TreeSet<>(loadedWorlds)) {
                EntityWorldSnapshot retained = retainedWorlds.contains(world)
                    ? current.get(world)
                    : null;
                reconciled.put(
                    world,
                    retained == null
                        ? aggregates.get(world).snapshot()
                        : retained
                );
            }

            long corrections = initialized
                ? countCorrections(current, reconciled)
                : 0L;
            publish(reconciled, capturedAt);
            initialized = true;
            activeRun = null;
            return new ReconciliationCommit(corrections, scan.duration());
        }
    }

    void abort(long runId) {
        synchronized (lock) {
            if (activeRun != null && activeRun.runId == runId) {
                activeRun = null;
            }
        }
    }

    SnapshotRepository<EntityWorldSnapshot> repository() {
        return repository;
    }

    boolean initialized() {
        synchronized (lock) {
            return initialized;
        }
    }

    private boolean recordEntity(
        EntityStateDelta.Kind kind,
        UUID identity,
        EntityDescriptor descriptor
    ) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(descriptor, "descriptor");
        synchronized (lock) {
            if (!accepting) {
                return false;
            }
            EntityStateDelta delta = EntityStateDelta.entity(
                Math.incrementExact(sequence),
                kind,
                identity,
                descriptor
            );
            journal(delta);
            if (initialized) {
                Map<String, EntityWorldSnapshot> updated = new LinkedHashMap<>(
                    current
                );
                if (kind == EntityStateDelta.Kind.ADD) {
                    EntityWorldSnapshot previous = updated.getOrDefault(
                        descriptor.world(),
                        EntityWorldSnapshot.empty(descriptor.world())
                    );
                    updated.put(
                        descriptor.world(),
                        apply(previous, descriptor, true)
                    );
                } else {
                    EntityWorldSnapshot previous = updated.get(descriptor.world());
                    if (previous != null) {
                        updated.put(
                            descriptor.world(),
                            apply(previous, descriptor, false)
                        );
                    }
                }
                publish(updated, clock.instant());
            }
            return true;
        }
    }

    private boolean recordWorld(EntityStateDelta.Kind kind, String world) {
        String normalized = de.minecraftgilde.prometheus.minecraft.WorldLabel
            .normalize(world);
        synchronized (lock) {
            if (!accepting) {
                return false;
            }
            EntityStateDelta delta = EntityStateDelta.world(
                Math.incrementExact(sequence),
                kind,
                normalized
            );
            journal(delta);
            if (initialized) {
                Map<String, EntityWorldSnapshot> updated = new LinkedHashMap<>(
                    current
                );
                if (kind == EntityStateDelta.Kind.WORLD_LOAD) {
                    updated.putIfAbsent(
                        normalized,
                        EntityWorldSnapshot.empty(normalized)
                    );
                } else {
                    updated.remove(normalized);
                }
                publish(updated, clock.instant());
            }
            return true;
        }
    }

    private void journal(EntityStateDelta delta) {
        if (activeRun != null) {
            activeRun.deltas.add(delta);
        }
    }

    private void publish(
        Map<String, EntityWorldSnapshot> values,
        Instant capturedAt
    ) {
        Map<String, EntityWorldSnapshot> sorted = new LinkedHashMap<>();
        new TreeSet<>(values.keySet()).forEach(world -> sorted.put(
            world,
            values.get(world)
        ));
        List<EntityWorldSnapshot> snapshots = List.copyOf(sorted.values());
        repository.publish(new ImmutableSnapshot<>(capturedAt, snapshots));
        current = Map.copyOf(sorted);
    }

    private EntityWorldSnapshot apply(
        EntityWorldSnapshot previous,
        EntityDescriptor descriptor,
        boolean add
    ) {
        long oldGroup = previous.group(descriptor.group());
        if (!add && oldGroup == 0L) {
            return previous;
        }

        EnumMap<EntityGroup, Long> groups = new EnumMap<>(previous.groups());
        groups.put(
            descriptor.group(),
            add ? Math.incrementExact(oldGroup) : oldGroup - 1L
        );
        long living = previous.livingEntities();
        if (descriptor.living()) {
            living = add
                ? Math.incrementExact(living)
                : Math.max(0L, living - 1L);
        }
        Map<String, Long> types = new LinkedHashMap<>(previous.exactTypes());
        if (includeExactTypes) {
            long oldType = types.getOrDefault(descriptor.exactType(), 0L);
            if (add) {
                types.put(descriptor.exactType(), Math.incrementExact(oldType));
            } else if (oldType <= 1L) {
                types.remove(descriptor.exactType());
            } else {
                types.put(descriptor.exactType(), oldType - 1L);
            }
        }
        long total = groups.values()
            .stream()
            .mapToLong(Long::longValue)
            .reduce(0L, Math::addExact);
        return new EntityWorldSnapshot(
            previous.world(),
            groups,
            total,
            living,
            groups.get(EntityGroup.VILLAGER),
            groups.get(EntityGroup.ITEM),
            groups.get(EntityGroup.PROJECTILE),
            types
        );
    }

    private static Map<UUID, ObservedState> observationsByIdentity(
        List<EntityObservation> observations
    ) {
        Map<UUID, ObservedState> result = new HashMap<>();
        for (EntityObservation observation : observations) {
            result.merge(
                observation.identity(),
                new ObservedState(
                    observation.descriptor(),
                    observation.observedSequence()
                ),
                EntityStateStore::newer
            );
        }
        return result;
    }

    private static ObservedState newer(
        ObservedState first,
        ObservedState second
    ) {
        if (first.seenSequence != second.seenSequence) {
            return first.seenSequence > second.seenSequence ? first : second;
        }
        String firstKey = first.descriptor.world()
            + '\u0000'
            + first.descriptor.exactType();
        String secondKey = second.descriptor.world()
            + '\u0000'
            + second.descriptor.exactType();
        return firstKey.compareTo(secondKey) <= 0 ? first : second;
    }

    private static void replay(
        List<EntityStateDelta> deltas,
        Map<UUID, ObservedState> entities,
        Set<String> loadedWorlds,
        Set<String> retainedWorlds
    ) {
        for (EntityStateDelta delta : deltas) {
            switch (delta.kind()) {
                case WORLD_LOAD -> loadedWorlds.add(delta.world());
                case WORLD_UNLOAD -> {
                    loadedWorlds.remove(delta.world());
                    retainedWorlds.remove(delta.world());
                    entities.entrySet().removeIf(
                        entry -> entry.getValue().descriptor.world().equals(
                            delta.world()
                        )
                    );
                }
                case ADD -> {
                    ObservedState previous = entities.get(delta.identity());
                    if (
                        previous == null
                            || delta.sequence() > previous.seenSequence
                    ) {
                        loadedWorlds.add(delta.descriptor().world());
                        entities.put(
                            delta.identity(),
                            new ObservedState(
                                delta.descriptor(),
                                delta.sequence()
                            )
                        );
                    }
                }
                case REMOVE -> {
                    ObservedState previous = entities.get(delta.identity());
                    if (
                        previous != null
                            && delta.sequence() > previous.seenSequence
                            && previous.descriptor.world().equals(delta.world())
                    ) {
                        entities.remove(delta.identity());
                    }
                }
            }
        }
    }

    private long countCorrections(
        Map<String, EntityWorldSnapshot> before,
        Map<String, EntityWorldSnapshot> after
    ) {
        long corrections = 0L;
        Set<String> worlds = new TreeSet<>(before.keySet());
        worlds.addAll(after.keySet());
        for (String world : worlds) {
            EntityWorldSnapshot oldValue = before.getOrDefault(
                world,
                EntityWorldSnapshot.empty(world)
            );
            EntityWorldSnapshot newValue = after.getOrDefault(
                world,
                EntityWorldSnapshot.empty(world)
            );
            for (EntityGroup group : EntityGroup.values()) {
                corrections = difference(
                    corrections,
                    oldValue.group(group),
                    newValue.group(group)
                );
            }
            corrections = difference(
                corrections,
                oldValue.totalEntities(),
                newValue.totalEntities()
            );
            corrections = difference(
                corrections,
                oldValue.livingEntities(),
                newValue.livingEntities()
            );
            corrections = difference(
                corrections,
                oldValue.villagers(),
                newValue.villagers()
            );
            corrections = difference(
                corrections,
                oldValue.itemEntities(),
                newValue.itemEntities()
            );
            if (includeExactTypes) {
                Set<String> types = new TreeSet<>(oldValue.exactTypes().keySet());
                types.addAll(newValue.exactTypes().keySet());
                for (String type : types) {
                    corrections = difference(
                        corrections,
                        oldValue.exactTypes().getOrDefault(type, 0L),
                        newValue.exactTypes().getOrDefault(type, 0L)
                    );
                }
            }
        }
        return corrections;
    }

    private static long difference(long count, long before, long after) {
        return before == after ? count : Math.incrementExact(count);
    }

    record ReconciliationCommit(long corrections, java.time.Duration duration) {}

    private static final class ReconciliationJournal {

        private final long runId;
        private final List<EntityStateDelta> deltas = new ArrayList<>();

        private ReconciliationJournal(long runId) {
            this.runId = runId;
        }
    }

    private record ObservedState(
        EntityDescriptor descriptor,
        long seenSequence
    ) {}

    private static final class MutableWorld {

        private final String world;
        private final EnumMap<EntityGroup, Long> groups = new EnumMap<>(
            EntityGroup.class
        );
        private final Map<String, Long> exactTypes = new HashMap<>();
        private long living;

        private MutableWorld(String world) {
            this.world = world;
            for (EntityGroup group : EntityGroup.values()) {
                groups.put(group, 0L);
            }
        }

        private void add(EntityDescriptor descriptor, boolean includeExactTypes) {
            groups.compute(
                descriptor.group(),
                (ignored, value) -> Math.incrementExact(value)
            );
            if (descriptor.living()) {
                living = Math.incrementExact(living);
            }
            if (includeExactTypes) {
                exactTypes.compute(
                    descriptor.exactType(),
                    (ignored, value) -> value == null
                        ? 1L
                        : Math.incrementExact(value)
                );
            }
        }

        private EntityWorldSnapshot snapshot() {
            long total = groups.values()
                .stream()
                .mapToLong(Long::longValue)
                .reduce(0L, Math::addExact);
            return new EntityWorldSnapshot(
                world,
                groups,
                total,
                living,
                groups.get(EntityGroup.VILLAGER),
                groups.get(EntityGroup.ITEM),
                groups.get(EntityGroup.PROJECTILE),
                exactTypes
            );
        }
    }
}
