package de.minecraftgilde.prometheus.folia.provider;

import de.minecraftgilde.prometheus.collector.SnapshotCapture;
import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.ObservationSourcesConfiguration;
import de.minecraftgilde.prometheus.minecraft.WorldLabel;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import de.minecraftgilde.prometheus.scheduler.CollectionTask;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
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

    private final Server server;
    private final CollectionScheduler scheduler;
    private final RegionObservationRegistry registry;
    private final Clock clock;
    private final Duration ttl;
    private final ObservationSourcesConfiguration sources;
    private final List<TpsWindow> windows;

    FoliaRegionSnapshotCapture(
        Server server,
        CollectionScheduler scheduler,
        RegionObservationRegistry registry,
        Clock clock,
        Duration ttl,
        ObservationSourcesConfiguration sources,
        List<TpsWindow> windows
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.windows = List.copyOf(Objects.requireNonNull(windows, "windows"));
    }

    @Override
    public void capture(SnapshotCompletion<RegionObservation> completion) {
        Objects.requireNonNull(completion, "completion");
        registry.current(clock.instant(), ttl);
        OptionalLong runId = registry.beginRun();
        if (runId.isEmpty()) {
            completion.failure(
                new IllegalStateException(
                    "Folia observation registry cannot begin a new run"
                )
            );
            return;
        }
        CaptureRun run = new CaptureRun(runId.orElseThrow(), completion);
        completion.whenInactive(run::abort);
        run.captureAnchors();
    }

    private final class CaptureRun {

        private final long runId;
        private final SnapshotCompletion<RegionObservation> completion;
        private final AtomicBoolean active = new AtomicBoolean(true);
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
                    AtomicBoolean completed = new AtomicBoolean();
                    Runnable retired = () -> playerFinished(completed);
                    Optional<CollectionTask> task = scheduler.executeFor(
                        player,
                        () -> {
                            try {
                                if (isActive()) {
                                    Location location = player.getLocation();
                                    World world = location.getWorld();
                                    if (world != null) {
                                        addAnchor(
                                            world,
                                            location.getBlockX() >> 4,
                                            location.getBlockZ() >> 4,
                                            1
                                        );
                                    }
                                }
                            } catch (Throwable failure) {
                                fail(failure);
                            } finally {
                                playerFinished(completed);
                            }
                        },
                        retired
                    );
                    if (task.isPresent()) {
                        track(task.orElseThrow());
                    } else {
                        playerFinished(completed);
                    }
                }
            } catch (Throwable failure) {
                fail(failure);
            }
        }

        private void playerFinished(AtomicBoolean completed) {
            if (
                completed.compareAndSet(false, true)
                    && remainingPlayers.decrementAndGet() == 0
                    && isActive()
            ) {
                beginRegionSampling();
            }
        }

        private void beginRegionSampling() {
            if (!isActive()) {
                return;
            }
            List<Anchor> candidates = anchors.values()
                .stream()
                .sorted(
                    Comparator.comparing((Anchor value) -> value.key().world())
                        .thenComparingInt(value -> value.key().chunkX())
                        .thenComparingInt(value -> value.key().chunkZ())
                )
                .toList();
            remainingRegions.set(candidates.size());
            if (candidates.isEmpty()) {
                succeed();
                return;
            }

            for (int index = 0; index < candidates.size(); index++) {
                Anchor candidate = candidates.get(index);
                int candidateIndex = index;
                try {
                    CollectionTask task = scheduler.executeAt(
                        candidate.world(),
                        candidate.key().chunkX(),
                        candidate.key().chunkZ(),
                        () -> sampleRegion(candidates, candidateIndex)
                    );
                    track(task);
                } catch (Throwable failure) {
                    fail(failure);
                    return;
                }
            }
        }

        private void sampleRegion(List<Anchor> candidates, int index) {
            try {
                if (!isActive()) {
                    return;
                }
                Anchor candidate = candidates.get(index);
                if (
                    !server.isOwnedByCurrentRegion(
                        candidate.world(),
                        candidate.key().chunkX(),
                        candidate.key().chunkZ()
                    )
                ) {
                    return;
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
                        return;
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

                double[] apiValues = server.getRegionTPS(
                    candidate.world(),
                    candidate.key().chunkX(),
                    candidate.key().chunkZ()
                );
                if (apiValues != null) {
                    registry.update(
                        runId,
                        new RegionObservation(
                            new RegionObservationKey(
                                candidate.key().world(),
                                candidate.key().chunkX(),
                                candidate.key().chunkZ()
                            ),
                            clock.instant(),
                            TpsWindow.read(apiValues, windows),
                            players
                        )
                    );
                }
            } catch (Throwable failure) {
                fail(failure);
            } finally {
                if (remainingRegions.decrementAndGet() == 0 && isActive()) {
                    succeed();
                }
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
            if (!isActive()) {
                return;
            }
            Optional<List<RegionObservation>> values = registry.completeRun(runId);
            if (values.isEmpty()) {
                return;
            }
            if (active.compareAndSet(true, false)) {
                completion.success(values.orElseThrow());
            }
        }

        private void fail(Throwable failure) {
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

        private void track(CollectionTask task) {
            if (!isActive()) {
                task.cancel();
                return;
            }
            tasks.add(task);
            if (!isActive() && tasks.remove(task)) {
                task.cancel();
            }
        }

        private void cancelTasks() {
            for (CollectionTask task : Set.copyOf(tasks)) {
                task.cancel();
            }
            tasks.clear();
        }
    }

    private record AnchorKey(String world, int chunkX, int chunkZ) {}

    private record Anchor(
        AnchorKey key,
        World world,
        int players
    ) {}
}
