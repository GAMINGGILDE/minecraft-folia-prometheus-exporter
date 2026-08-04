package de.minecraftgilde.prometheus.minecraft.entity;

import de.minecraftgilde.prometheus.collector.SnapshotCapture;
import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.minecraft.WorldLabel;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import de.minecraftgilde.prometheus.scheduler.CollectionTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * Reconciles loaded entities by distributing chunk and entity access to owners.
 */
final class BukkitEntityReconciliationCapture
    implements SnapshotCapture<EntityScanResult> {

    static final String WORLD_FAILURE_MESSAGE =
        "One entity reconciliation world could not be enumerated and was retained.";
    static final String CHUNK_FAILURE_MESSAGE =
        "One entity reconciliation chunk was skipped.";
    static final String ENTITY_FAILURE_MESSAGE =
        "One entity reconciliation observation was skipped.";

    private final Server server;
    private final CollectionScheduler scheduler;
    private final EntityStateStore stateStore;
    private final EntityGroupClassifier classifier;
    private final LongSupplier nanoTime;
    private final BiConsumer<String, Throwable> failureReporter;

    BukkitEntityReconciliationCapture(
        Server server,
        CollectionScheduler scheduler,
        EntityStateStore stateStore,
        EntityGroupClassifier classifier,
        LongSupplier nanoTime,
        BiConsumer<String, Throwable> failureReporter
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.failureReporter = Objects.requireNonNull(
            failureReporter,
            "failureReporter"
        );
    }

    @Override
    public void capture(SnapshotCompletion<EntityScanResult> completion) {
        Objects.requireNonNull(completion, "completion");
        OptionalLong runId;
        try {
            runId = stateStore.beginReconciliation();
        } catch (Throwable failure) {
            completion.failure(sanitized(
                "Entity reconciliation could not begin.",
                failure
            ));
            return;
        }
        if (runId.isEmpty()) {
            completion.failure(
                new IllegalStateException(
                    "Entity reconciliation state rejected a new run"
                )
            );
            return;
        }

        CaptureRun run = new CaptureRun(
            runId.orElseThrow(),
            completion,
            nanoTime.getAsLong()
        );
        try {
            completion.whenInactive(run::abort);
            run.captureGlobalTopology();
        } catch (Throwable failure) {
            run.failSystemically(failure);
        }
    }

    private final class CaptureRun {

        private final long runId;
        private final SnapshotCompletion<EntityScanResult> completion;
        private final long startedNanos;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicInteger pendingWork = new AtomicInteger(1);
        private final Set<String> loadedWorlds = ConcurrentHashMap.newKeySet();
        private final Set<String> retainedWorlds = ConcurrentHashMap.newKeySet();
        private final Map<String, AtomicInteger> scheduledChunks =
            new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> successfulChunks =
            new ConcurrentHashMap<>();
        private final Map<UUID, EntityObservation> observations =
            new ConcurrentHashMap<>();
        private final Set<CollectionTask> tasks = ConcurrentHashMap.newKeySet();

        private CaptureRun(
            long runId,
            SnapshotCompletion<EntityScanResult> completion,
            long startedNanos
        ) {
            this.runId = runId;
            this.completion = completion;
            this.startedNanos = startedNanos;
        }

        private void captureGlobalTopology() {
            if (!isActive()) {
                finishWork();
                return;
            }
            try {
                for (World world : List.copyOf(server.getWorlds())) {
                    captureWorld(world);
                }
            } catch (Throwable failure) {
                failSystemically(failure);
                return;
            }
            finishWork();
        }

        private void captureWorld(World world) {
            String worldLabel;
            try {
                worldLabel = WorldLabel.normalize(world.getName());
                loadedWorlds.add(worldLabel);
            } catch (RuntimeException failure) {
                reportLocalFailure(
                    "entity-world-reconciliation",
                    WORLD_FAILURE_MESSAGE,
                    failure
                );
                return;
            }

            Chunk[] chunks;
            try {
                chunks = Objects.requireNonNull(
                    world.getLoadedChunks(),
                    "loaded chunks"
                );
            } catch (RuntimeException failure) {
                retainedWorlds.add(worldLabel);
                reportLocalFailure(
                    "entity-world-reconciliation",
                    WORLD_FAILURE_MESSAGE,
                    failure
                );
                return;
            }

            for (Chunk chunk : chunks.clone()) {
                if (!isActive()) {
                    return;
                }
                scheduleChunk(worldLabel, Objects.requireNonNull(chunk, "chunk"));
            }
        }

        private void scheduleChunk(String worldLabel, Chunk chunk) {
            ChunkWork work = new ChunkWork(worldLabel);
            registerWork();
            scheduledChunks.computeIfAbsent(
                worldLabel,
                ignored -> new AtomicInteger()
            ).incrementAndGet();
            try {
                int chunkX = chunk.getX();
                int chunkZ = chunk.getZ();
                CollectionTask task = scheduler.executeAt(
                    chunk.getWorld(),
                    chunkX,
                    chunkZ,
                    () -> sampleChunk(chunk, work)
                );
                if (task == null) {
                    chunkFinished(
                        work,
                        new IllegalStateException(
                            "Region scheduler rejected an entity chunk task"
                        )
                    );
                } else {
                    track(work, task);
                }
            } catch (RuntimeException failure) {
                chunkFinished(work, failure);
            } catch (Throwable failure) {
                failSystemically(failure);
            }
        }

        private void sampleChunk(Chunk chunk, ChunkWork work) {
            if (!work.invoked.compareAndSet(false, true) || !isActive()) {
                chunkFinished(work, null);
                return;
            }
            RuntimeException localFailure = null;
            try {
                if (chunk.isLoaded() && chunk.isEntitiesLoaded()) {
                    Entity[] entities = Objects.requireNonNull(
                        chunk.getEntities(),
                        "chunk entities"
                    );
                    for (Entity entity : entities.clone()) {
                        scheduleEntity(Objects.requireNonNull(entity, "entity"));
                    }
                }
                successfulChunks.computeIfAbsent(
                    work.world,
                    ignored -> new AtomicInteger()
                ).incrementAndGet();
            } catch (RuntimeException failure) {
                localFailure = failure;
            } catch (Throwable failure) {
                failSystemically(failure);
            } finally {
                chunkFinished(work, localFailure);
            }
        }

        private void scheduleEntity(Entity entity) {
            EntityWork work = new EntityWork();
            registerWork();
            try {
                Optional<CollectionTask> task = scheduler.executeFor(
                    entity,
                    () -> sampleEntity(entity, work),
                    () -> entityFinished(
                        work,
                        new IllegalStateException(
                            "Entity scheduler retired an observation"
                        )
                    )
                );
                if (task.isPresent()) {
                    track(work, task.orElseThrow());
                } else {
                    entityFinished(
                        work,
                        new IllegalStateException(
                            "Entity scheduler rejected an observation"
                        )
                    );
                }
            } catch (RuntimeException failure) {
                entityFinished(work, failure);
            } catch (Throwable failure) {
                failSystemically(failure);
            }
        }

        private void sampleEntity(Entity entity, EntityWork work) {
            if (!work.invoked.compareAndSet(false, true) || !isActive()) {
                entityFinished(work, null);
                return;
            }
            RuntimeException localFailure = null;
            try {
                String world = entity.getWorld().getName();
                EntityDescriptor.observe(
                    entity,
                    world,
                    classifier,
                    stateStore::currentSequence
                ).ifPresent(observation -> observations.merge(
                    observation.identity(),
                    observation,
                    BukkitEntityReconciliationCapture::newer
                ));
            } catch (RuntimeException failure) {
                localFailure = failure;
            } catch (Throwable failure) {
                failSystemically(failure);
            } finally {
                entityFinished(work, localFailure);
            }
        }

        private void chunkFinished(
            ChunkWork work,
            RuntimeException localFailure
        ) {
            if (!work.completed.compareAndSet(false, true)) {
                return;
            }
            untrack(work);
            if (localFailure != null) {
                reportLocalFailure(
                    "entity-chunk-reconciliation",
                    CHUNK_FAILURE_MESSAGE,
                    localFailure
                );
            }
            finishWork();
        }

        private void entityFinished(
            EntityWork work,
            RuntimeException localFailure
        ) {
            if (!work.completed.compareAndSet(false, true)) {
                return;
            }
            untrack(work);
            if (localFailure != null) {
                reportLocalFailure(
                    "entity-observation",
                    ENTITY_FAILURE_MESSAGE,
                    localFailure
                );
            }
            finishWork();
        }

        private void registerWork() {
            int value = pendingWork.incrementAndGet();
            if (value <= 1) {
                failSystemically(
                    new IllegalStateException(
                        "Entity reconciliation work count overflowed"
                    )
                );
            }
        }

        private void finishWork() {
            int remaining = pendingWork.decrementAndGet();
            if (remaining < 0) {
                failSystemically(
                    new IllegalStateException(
                        "Entity reconciliation work count became negative"
                    )
                );
            } else if (remaining == 0) {
                succeed();
            }
        }

        private void succeed() {
            if (!isActive() || !active.compareAndSet(true, false)) {
                return;
            }
            for (Map.Entry<String, AtomicInteger> entry : scheduledChunks.entrySet()) {
                int successes = successfulChunks
                    .getOrDefault(entry.getKey(), new AtomicInteger())
                    .get();
                if (entry.getValue().get() > 0 && successes == 0) {
                    retainedWorlds.add(entry.getKey());
                }
            }
            List<EntityObservation> values = new ArrayList<>(observations.values());
            values.sort(
                Comparator.comparing(
                    (EntityObservation value) -> value.descriptor().world()
                )
                    .thenComparing(value -> value.descriptor().exactType())
                    .thenComparing(EntityObservation::identity)
            );
            long elapsed = Math.max(0L, nanoTime.getAsLong() - startedNanos);
            EntityScanResult result = new EntityScanResult(
                runId,
                Set.copyOf(loadedWorlds),
                Set.copyOf(retainedWorlds),
                values,
                Duration.ofNanos(elapsed)
            );
            boolean accepted;
            try {
                accepted = completion.successIfActive(() -> List.of(result));
            } catch (Throwable failure) {
                accepted = false;
                if (completion.isActive()) {
                    completion.failure(sanitized(
                        "Entity reconciliation commit failed.",
                        failure
                    ));
                }
            }
            if (!accepted) {
                stateStore.abort(runId);
                cancelTasks();
            } else {
                tasks.clear();
            }
        }

        private void failSystemically(Throwable failure) {
            Objects.requireNonNull(failure, "failure");
            if (active.compareAndSet(true, false)) {
                stateStore.abort(runId);
                cancelTasks();
                completion.failure(sanitized(
                    "Entity reconciliation failed systemically.",
                    failure
                ));
            }
        }

        private void abort() {
            if (active.compareAndSet(true, false)) {
                stateStore.abort(runId);
                cancelTasks();
            }
        }

        private boolean isActive() {
            return active.get() && completion.isActive();
        }

        private void reportLocalFailure(
            String key,
            String message,
            RuntimeException failure
        ) {
            try {
                failureReporter.accept(key, sanitized(message, failure));
            } catch (RuntimeException ignored) {
                // A diagnostic observer must never escape an owning scheduler.
            }
        }

        private void track(Work work, CollectionTask task) {
            Objects.requireNonNull(task, "task");
            if (!work.task.compareAndSet(null, task)) {
                safeCancel(task);
                failSystemically(
                    new IllegalStateException(
                        "Entity reconciliation task was attached twice"
                    )
                );
                return;
            }
            if (work.completed.get()) {
                return;
            }
            tasks.add(task);
            if (work.completed.get()) {
                tasks.remove(task);
            } else if (!isActive() && tasks.remove(task)) {
                safeCancel(task);
            }
        }

        private void untrack(Work work) {
            CollectionTask task = work.task.get();
            if (task != null) {
                tasks.remove(task);
            }
        }

        private void cancelTasks() {
            for (CollectionTask task : Set.copyOf(tasks)) {
                safeCancel(task);
            }
            tasks.clear();
        }

        private void safeCancel(CollectionTask task) {
            try {
                task.cancel();
            } catch (RuntimeException ignored) {
                // Continue cancelling remaining work.
            }
        }
    }

    private static EntityObservation newer(
        EntityObservation first,
        EntityObservation second
    ) {
        if (first.observedSequence() != second.observedSequence()) {
            return first.observedSequence() > second.observedSequence()
                ? first
                : second;
        }
        return first.descriptor().world().compareTo(second.descriptor().world()) <= 0
            ? first
            : second;
    }

    private static IllegalStateException sanitized(
        String message,
        Throwable failure
    ) {
        IllegalStateException result = new IllegalStateException(message);
        result.setStackTrace(failure.getStackTrace());
        return result;
    }

    private static class Work {

        final AtomicBoolean invoked = new AtomicBoolean();
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicReference<CollectionTask> task = new AtomicReference<>();
    }

    private static final class ChunkWork extends Work {

        private final String world;

        private ChunkWork(String world) {
            this.world = world;
        }
    }

    private static final class EntityWork extends Work {}
}
