package de.minecraftgilde.prometheus.minecraft;

import de.minecraftgilde.prometheus.collector.SnapshotCapture;
import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.bukkit.Server;
import org.bukkit.World;

/** Captures normalized paths globally and computes their sizes only asynchronously. */
public final class BukkitWorldSizeSnapshotCapture
    implements SnapshotCapture<WorldSizeSnapshot> {

    private final Server server;
    private final CollectionScheduler scheduler;
    private final WorldSizeCalculator calculator;
    private final SnapshotRepository<WorldSizeSnapshot> repository;
    private final Consumer<Throwable> failureListener;
    private final Set<Path> calculationsInProgress = ConcurrentHashMap.newKeySet();

    public BukkitWorldSizeSnapshotCapture(
        Server server,
        CollectionScheduler scheduler,
        WorldSizeCalculator calculator,
        SnapshotRepository<WorldSizeSnapshot> repository,
        Consumer<Throwable> failureListener
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.failureListener = Objects.requireNonNull(
            failureListener,
            "failureListener"
        );
    }

    @Override
    public void capture(SnapshotCompletion<WorldSizeSnapshot> completion) {
        Objects.requireNonNull(completion, "completion");
        final DirectoryCapture directoryCapture;
        try {
            directoryCapture = captureDirectories();
        } catch (Throwable failure) {
            completion.failure(
                new IllegalStateException("World path capture failed")
            );
            return;
        }

        Map<String, WorldSizeSnapshot> previous = previousByWorld();
        Map<String, WorldSizeSnapshot> results = new ConcurrentHashMap<>();
        directoryCapture.failedWorlds().forEach(
            world -> retainPrevious(world, previous, results)
        );
        Map<String, Path> directories = directoryCapture.directories();
        if (directories.isEmpty()) {
            List<WorldSizeSnapshot> complete = new ArrayList<>(results.values());
            complete.sort(java.util.Comparator.comparing(WorldSizeSnapshot::world));
            completion.success(complete);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(directories.size());
        AtomicBoolean completed = new AtomicBoolean();

        Runnable finishOne = () -> {
            if (remaining.decrementAndGet() != 0 || !completed.compareAndSet(false, true)) {
                return;
            }
            List<WorldSizeSnapshot> complete = new ArrayList<>(results.values());
            complete.sort(java.util.Comparator.comparing(WorldSizeSnapshot::world));
            completion.success(complete);
        };

        directories.forEach((world, directory) -> {
            if (!calculationsInProgress.add(directory)) {
                retainPrevious(world, previous, results);
                finishOne.run();
                return;
            }
            try {
                scheduler.executeAsync(() -> {
                    try {
                        WorldSizeCalculator.Result result = calculator.calculate(directory);
                        results.put(
                            world,
                            new WorldSizeSnapshot(world, result.bytes())
                        );
                        if (result.skippedEntries() > 0) {
                            failureListener.accept(
                                new IllegalStateException(
                                    "World size for '" + world + "' skipped "
                                        + result.skippedEntries() + " unreadable or vanished entries"
                                )
                            );
                        }
                    } catch (Throwable failure) {
                        retainPrevious(world, previous, results);
                        failureListener.accept(
                            new IllegalStateException(
                                "Could not calculate world size for '" + world + "'",
                                failure
                            )
                        );
                    } finally {
                        calculationsInProgress.remove(directory);
                        finishOne.run();
                    }
                });
            } catch (Throwable schedulingFailure) {
                calculationsInProgress.remove(directory);
                retainPrevious(world, previous, results);
                failureListener.accept(schedulingFailure);
                finishOne.run();
            }
        });
    }

    int calculationsInProgress() {
        return calculationsInProgress.size();
    }

    private DirectoryCapture captureDirectories() {
        Map<String, Path> directories = new LinkedHashMap<>();
        Set<String> failedWorlds = new LinkedHashSet<>();
        for (World world : List.copyOf(server.getWorlds())) {
            String name = null;
            try {
                name = world.getName();
                if (directories.containsKey(name)) {
                    continue;
                }
                directories.put(
                    name,
                    world.getWorldPath().toAbsolutePath().normalize()
                );
            } catch (Throwable worldFailure) {
                failureListener.accept(
                    new IllegalStateException(
                        name == null
                            ? "Could not identify one world for size capture"
                            : "Could not capture world path for '" + name + "'"
                    )
                );
                if (name != null) {
                    failedWorlds.add(name);
                }
            }
        }
        return new DirectoryCapture(directories, failedWorlds);
    }

    private Map<String, WorldSizeSnapshot> previousByWorld() {
        Map<String, WorldSizeSnapshot> result = new HashMap<>();
        repository
            .current()
            .ifPresent(snapshot -> snapshot.values().forEach(
                world -> result.put(world.world(), world)
            ));
        return result;
    }

    private static void retainPrevious(
        String world,
        Map<String, WorldSizeSnapshot> previous,
        Map<String, WorldSizeSnapshot> results
    ) {
        WorldSizeSnapshot value = previous.get(world);
        if (value != null) {
            results.put(world, value);
        }
    }

    private record DirectoryCapture(
        Map<String, Path> directories,
        Set<String> failedWorlds
    ) {

        private DirectoryCapture {
            directories = Map.copyOf(directories);
            failedWorlds = Set.copyOf(failedWorlds);
        }
    }
}
