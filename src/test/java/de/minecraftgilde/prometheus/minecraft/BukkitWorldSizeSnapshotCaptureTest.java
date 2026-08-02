package de.minecraftgilde.prometheus.minecraft;

import static de.minecraftgilde.prometheus.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.scheduler.ManualCollectionScheduler;
import de.minecraftgilde.prometheus.snapshot.ImmutableSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BukkitWorldSizeSnapshotCaptureTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void preventsOverlappingCalculationsForTheSameWorld() throws Exception {
        Path worldPath = Files.createDirectory(temporaryDirectory.resolve("world"));
        Files.write(worldPath.resolve("level.dat"), new byte[11]);
        World world = world("world", worldPath);
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler(false);
        SnapshotRepository<WorldSizeSnapshot> repository = new SnapshotRepository<>();
        repository.publish(
            new ImmutableSnapshot<>(
                Instant.EPOCH,
                List.of(new WorldSizeSnapshot("world", 5))
            )
        );
        BukkitWorldSizeSnapshotCapture capture = new BukkitWorldSizeSnapshotCapture(
            server(List.of(world)),
            scheduler,
            new WorldSizeCalculator(),
            repository,
            failure -> {}
        );
        TestCompletion first = new TestCompletion();
        TestCompletion second = new TestCompletion();

        capture.capture(first);
        capture.capture(second);

        assertEquals(1, scheduler.queuedAsyncTasks());
        assertEquals(1, capture.calculationsInProgress());
        assertEquals(List.of(new WorldSizeSnapshot("world", 5)), second.values.get());

        scheduler.runAsync();
        assertEquals(List.of(new WorldSizeSnapshot("world", 11)), first.values.get());
        assertEquals(0, capture.calculationsInProgress());
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
        SnapshotRepository<WorldSizeSnapshot> repository = new SnapshotRepository<>();
        repository.publish(
            new ImmutableSnapshot<>(
                Instant.EPOCH,
                List.of(new WorldSizeSnapshot("world", 42))
            )
        );
        List<Throwable> failures = new ArrayList<>();
        BukkitWorldSizeSnapshotCapture capture = new BukkitWorldSizeSnapshotCapture(
            server,
            new ManualCollectionScheduler(),
            new WorldSizeCalculator(),
            repository,
            failures::add
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
        SnapshotRepository<WorldSizeSnapshot> repository = new SnapshotRepository<>();
        repository.publish(
            new ImmutableSnapshot<>(
                Instant.EPOCH,
                List.of(new WorldSizeSnapshot("broken", 42))
            )
        );
        List<Throwable> failures = new ArrayList<>();
        BukkitWorldSizeSnapshotCapture capture = new BukkitWorldSizeSnapshotCapture(
            server(List.of(broken, world("healthy", healthyPath))),
            new ManualCollectionScheduler(),
            new WorldSizeCalculator(),
            repository,
            failures::add
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

    private static Server server(List<World> worlds) {
        return proxy(Server.class, Map.of("getWorlds", worlds));
    }

    private static World world(String name, Path path) {
        return proxy(
            World.class,
            Map.of("getName", name, "getWorldPath", path)
        );
    }

    private static final class TestCompletion
        implements SnapshotCompletion<WorldSizeSnapshot> {

        private final AtomicReference<List<WorldSizeSnapshot>> values =
            new AtomicReference<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        public void success(List<WorldSizeSnapshot> result) {
            values.set(result);
        }

        @Override
        public void failure(Throwable result) {
            failure.set(result);
        }
    }
}
