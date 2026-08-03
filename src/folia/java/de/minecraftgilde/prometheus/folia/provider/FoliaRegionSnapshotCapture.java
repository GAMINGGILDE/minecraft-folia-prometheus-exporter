package de.minecraftgilde.prometheus.folia.provider;

import de.minecraftgilde.prometheus.collector.SnapshotCapture;
import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.ObservationSourcesConfiguration;
import de.minecraftgilde.prometheus.minecraft.WorldLabel;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import de.minecraftgilde.prometheus.scheduler.CollectionTask;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Samples public anchors and deduplicates them by public current-region ownership.
 */
final class FoliaRegionSnapshotCapture
    implements SnapshotCapture<RegionObservation> {

    static final String PLAYER_FAILURE_MESSAGE =
        "A Folia player anchor observation failed and was skipped.";
    static final String REGION_FAILURE_MESSAGE =
        "A Folia region observation failed and was skipped.";

    private static final String PLAYER_FAILURE_KEY =
        "folia-player-observation";
    private static final String REGION_FAILURE_KEY =
        "folia-region-observation";

    private final Server server;
    private final CollectionScheduler scheduler;
    private final RegionObservationRegistry registry;
    private final Clock clock;
    private final Duration ttl;
    private final ObservationSourcesConfiguration sources;
    private final List<TpsWindow> windows;
    private final BiConsumer<String, Throwable> failureReporter;

    FoliaRegionSnapshotCapture(
        Server server,
        CollectionScheduler scheduler,
        RegionObservationRegistry registry,
        Clock clock,
        Duration ttl,
        ObservationSourcesConfiguration sources,
        List<TpsWindow> windows,
        BiConsumer<String, Throwable> failureReporter
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.windows = List.copyOf(Objects.requireNonNull(windows, "windows"));
        this.failureReporter = Objects.requireNonNull(
            failureReporter,
            "failureReporter"
        );
    }

    @Override
    public void capture(SnapshotCompletion<RegionObservation> completion) {
        Objects.requireNonNull(completion, "completion");
        OptionalLong runId;
        try {
            registry.current(clock.instant(), ttl);
            runId = registry.beginRun();
        } catch (Throwable failure) {
            completion.failure(failure);
            return;
        }
        if (runId.isEmpty()) {
            completion.failure(
                new IllegalStateException(
                    "Folia observation registry cannot begin a new run"
                )
            );
            return;
        }

        CaptureRun run = new CaptureRun(runId.orElseThrow(), completion);
        try {
            completion.whenInactive(run::abort);
        } catch (Throwable failure) {
            run.failSystemically(failure);
            return;
        }
        run.captureAnchors();
    }

    private final class CaptureRun {

        private final long runId;
        private final SnapshotCompletion<RegionObservation> completion;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicBoolean regionSamplingStarted = new AtomicBoolean();
        private final AtomicInteger remainingPlayers = new AtomicInteger();
        private final AtomicInteger remainingRegions = new AtomicInteger();
        private final Map<AnchorKey, Anchor> anchors = new ConcurrentHashMap<>();
        private final Set<CollectionTask> tasks = ConcurrentHashMap.newKeySet();

        private CaptureRun(
            long runId,
            SnapshotCompletion<RegionObservation> completion
        ) {
            this.runId = runId;
            this.completion = completion;
        }

        private void captureAnchors() {
            if (!isActive()) {
                return;
            }
            try {
                List<World> worlds = List.copyOf(server.getWorlds());
                for (World world : worlds) {
                    if (sources.worldSpawns()) {
                        Location spawn = world.getSpawnLocation();
                        addAnchor(
                            world,
                            spawn.getBlockX() >> 4,
                            spawn.getBlockZ() >> 4,
                            0
                        );
                    }
                    if (sources.forceLoadedChunks()) {
                        for (Chunk chunk : world.getForceLoadedChunks()) {
                            addAnchor(world, chunk.getX(), chunk.getZ(), 0);
                        }
                    }
                }

                if (!sources.playerRegions()) {
                    beginRegionSampling();
                    return;
                }
                List<Player> players = List.copyOf(server.getOnlinePlayers());
                remainingPlayers.set(players.size());
                if (players.isEmpty()) {
                    beginRegionSampling();
                    return;
                }
                for (Player player : players) {
                    if (!isActive()) {
                        return;
                    }
                    schedulePlayer(player);
                }
            } catch (Throwable failure) {
                failSystemically(failure);
            }
        }

        private void schedulePlayer(Player player) {
            PlayerWork work = new PlayerWork();
            try {
                Optional<CollectionTask> task = scheduler.executeFor(
                    player,
                    () -> samplePlayer(player, work),
                    () -> playerFinished(
                        work,
                        new IllegalStateException(
                            "A Folia player scheduler task retired"
                        )
                    )
                );
                if (task.isPresent()) {
                    track(work, task.orElseThrow());
                } else {
                    playerFinished(
                        work,
                        new IllegalStateException(
                            "A Folia player scheduler task was not accepted"
                        )
                    );
                }
            } catch (RuntimeException failure) {
                playerFinished(work, failure);
            } catch (Throwable failure) {
                failSystemically(failure);
            }
        }

        private void samplePlayer(Player player, PlayerWork work) {
            synchronized (work.lock) {
                if (work.completed.get() || !isActive()) {
                    return;
                }
                RuntimeException localFailure = null;
                try {
                    Location location = Objects.requireNonNull(
                        player.getLocation(),
                        "Player location"
                    );
                    World world = Objects.requireNonNull(
                        location.getWorld(),
                        "Player location world"
                    );
                    addAnchor(
                        world,
                        location.getBlockX() >> 4,
                        location.getBlockZ() >> 4,
                        1
                    );
                } catch (RuntimeException failure) {
                    localFailure = failure;
                } catch (Throwable failure) {
                    failSystemically(failure);
                } finally {
                    playerFinished(work, localFailure);
                }
            }
        }

        private void playerFinished(
            PlayerWork work,
            RuntimeException localFailure
        ) {
            int remaining;
            synchronized (work.lock) {
                if (!work.completed.compareAndSet(false, true)) {
                    return;
                }
                untrack(work);
                if (!isActive()) {
                    return;
                }
                remaining = remainingPlayers.decrementAndGet();
            }

            if (localFailure != null) {
                reportLocalFailure(
                    PLAYER_FAILURE_KEY,
                    PLAYER_FAILURE_MESSAGE,
                    localFailure
                );
            }
            if (remaining < 0) {
                failSystemically(
                    new IllegalStateException(
                        "Folia player completion count became negative"
                    )
                );
            } else if (remaining == 0) {
                beginRegionSampling();
            }
        }

        private void beginRegionSampling() {
            if (!isActive()) {
                return;
            }
            if (!regionSamplingStarted.compareAndSet(false, true)) {
                failSystemically(
                    new IllegalStateException(
                        "Folia region sampling started more than once"
                    )
                );
                return;
            }

            List<Anchor> candidates;
            try {
                candidates = anchors.values()
                    .stream()
                    .sorted(
                        Comparator.comparing((Anchor value) -> value.key().world())
                            .thenComparingInt(value -> value.key().chunkX())
                            .thenComparingInt(value -> value.key().chunkZ())
                    )
                    .toList();
            } catch (Throwable failure) {
                failSystemically(failure);
                return;
            }

            remainingRegions.set(candidates.size());
            if (candidates.isEmpty()) {
                succeed();
                return;
            }

            List<RegionWork> workItems = java.util.stream.IntStream
                .range(0, candidates.size())
                .mapToObj(RegionWork::new)
                .toList();
            for (RegionWork work : workItems) {
                if (!isActive()) {
                    return;
                }
                Anchor candidate = candidates.get(work.index);
                try {
                    CollectionTask task = scheduler.executeAt(
                        candidate.world(),
                        candidate.key().chunkX(),
                        candidate.key().chunkZ(),
                        () -> sampleRegion(candidates, work)
                    );
                    if (task == null) {
                        regionFinished(
                            work,
                            new IllegalStateException(
                                "A Folia region scheduler task was not accepted"
                            )
                        );
                    } else {
                        track(work, task);
                    }
                } catch (RuntimeException failure) {
                    regionFinished(work, failure);
                } catch (Throwable failure) {
                    failSystemically(failure);
                    return;
                }
            }
        }

        private void sampleRegion(
            List<Anchor> candidates,
            RegionWork work
        ) {
            if (
                work.completed.get()
                    || !work.invoked.compareAndSet(false, true)
                    || !isActive()
            ) {
                return;
            }

            RuntimeException localFailure = null;
            RegionObservation observation = null;
            try {
                observation = observeRegion(candidates, work.index);
            } catch (RuntimeException failure) {
                localFailure = failure;
            } catch (Throwable failure) {
                failSystemically(failure);
            }

            if (observation != null && isActive()) {
                try {
                    if (!registry.update(runId, observation) && isActive()) {
                        failSystemically(
                            new IllegalStateException(
                                "Folia observation registry rejected the active run"
                            )
                        );
                    }
                } catch (Throwable failure) {
                    failSystemically(failure);
                }
            }
            regionFinished(work, localFailure);
        }

        private RegionObservation observeRegion(
            List<Anchor> candidates,
            int index
        ) {
            Anchor candidate = candidates.get(index);
            if (
                !server.isOwnedByCurrentRegion(
                    candidate.world(),
                    candidate.key().chunkX(),
                    candidate.key().chunkZ()
                )
            ) {
                throw new IllegalStateException(
                    "A Folia region anchor is no longer owned"
                );
            }
            for (int earlierIndex = 0; earlierIndex < index; earlierIndex++) {
                Anchor earlier = candidates.get(earlierIndex);
                if (
                    earlier.key().world().equals(candidate.key().world())
                        && server.isOwnedByCurrentRegion(
                            candidate.world(),
                            earlier.key().chunkX(),
                            earlier.key().chunkZ()
                        )
                ) {
                    return null;
                }
            }

            int players = 0;
            for (Anchor anchor : candidates) {
                if (
                    anchor.players() > 0
                        && anchor.key().world().equals(candidate.key().world())
                        && server.isOwnedByCurrentRegion(
                            candidate.world(),
                            anchor.key().chunkX(),
                            anchor.key().chunkZ()
                        )
                ) {
                    players = Math.addExact(players, anchor.players());
                }
            }

            double[] apiValues = Objects.requireNonNull(
                server.getRegionTPS(
                    candidate.world(),
                    candidate.key().chunkX(),
                    candidate.key().chunkZ()
                ),
                "Public Folia region TPS result"
            );
            return new RegionObservation(
                new RegionObservationKey(
                    candidate.key().world(),
                    candidate.key().chunkX(),
                    candidate.key().chunkZ()
                ),
                clock.instant(),
                TpsWindow.read(apiValues, windows),
                players
            );
        }

        private void regionFinished(
            RegionWork work,
            RuntimeException localFailure
        ) {
            if (!work.completed.compareAndSet(false, true)) {
                return;
            }
            untrack(work);
            if (!isActive()) {
                return;
            }
            if (localFailure != null) {
                reportLocalFailure(
                    REGION_FAILURE_KEY,
                    REGION_FAILURE_MESSAGE,
                    localFailure
                );
            }
            int remaining = remainingRegions.decrementAndGet();
            if (remaining < 0) {
                failSystemically(
                    new IllegalStateException(
                        "Folia region completion count became negative"
                    )
                );
            } else if (remaining == 0) {
                succeed();
            }
        }

        private void addAnchor(
            World world,
            int chunkX,
            int chunkZ,
            int players
        ) {
            String worldLabel = WorldLabel.normalize(world.getName());
            AnchorKey key = new AnchorKey(worldLabel, chunkX, chunkZ);
            anchors.compute(key, (ignored, previous) -> new Anchor(
                key,
                previous == null ? world : previous.world(),
                Math.addExact(
                    previous == null ? 0 : previous.players(),
                    players
                )
            ));
        }

        private void succeed() {
            if (!isActive() || !active.compareAndSet(true, false)) {
                return;
            }

            boolean accepted;
            try {
                accepted = completion.successIfActive(() -> registry
                    .completeRun(runId)
                    .orElseThrow(() -> new IllegalStateException(
                        "Folia observation registry lost the active run"
                    ))
                );
            } catch (Throwable failure) {
                registry.failRun(runId);
                cancelTasks();
                if (completion.isActive()) {
                    completion.failure(failure);
                }
                return;
            }
            if (!accepted) {
                registry.failRun(runId);
                cancelTasks();
            } else {
                tasks.clear();
            }
        }

        private void failSystemically(Throwable failure) {
            Objects.requireNonNull(failure, "failure");
            if (active.compareAndSet(true, false)) {
                registry.failRun(runId);
                cancelTasks();
                completion.failure(failure);
            }
        }

        private void abort() {
            if (active.compareAndSet(true, false)) {
                registry.failRun(runId);
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
                IllegalStateException sanitized = new IllegalStateException(
                    message
                );
                sanitized.setStackTrace(failure.getStackTrace());
                failureReporter.accept(key, sanitized);
            } catch (RuntimeException ignored) {
                // A diagnostic observer must never escape a Minecraft scheduler.
            }
        }

        private void track(Work work, CollectionTask task) {
            Objects.requireNonNull(task, "task");
            if (!work.task.compareAndSet(null, task)) {
                safeCancel(task);
                failSystemically(
                    new IllegalStateException(
                        "A Folia capture task was attached more than once"
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
                // Continue cancelling every other task during systemic cleanup.
            }
        }
    }

    private static class Work {

        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicReference<CollectionTask> task =
            new AtomicReference<>();
    }

    private static final class PlayerWork extends Work {

        private final Object lock = new Object();
    }

    private static final class RegionWork extends Work {

        private final int index;
        private final AtomicBoolean invoked = new AtomicBoolean();

        private RegionWork(int index) {
            this.index = index;
        }
    }

    private record AnchorKey(String world, int chunkX, int chunkZ) {}

    private record Anchor(
        AnchorKey key,
        World world,
        int players
    ) {}
}
