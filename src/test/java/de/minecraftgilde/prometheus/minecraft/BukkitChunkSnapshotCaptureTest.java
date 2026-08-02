package de.minecraftgilde.prometheus.minecraft;

import static de.minecraftgilde.prometheus.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.snapshot.ImmutableSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class BukkitChunkSnapshotCaptureTest {

    @Test
    void usesOnlyAggregateChunkCountsAndIsolatesWorldFailures() {
        World good = proxy(
            World.class,
            Map.of("getName", "good", "getChunkCount", 17)
        );
        World broken = proxy(
            World.class,
            Map.of(
                "getName",
                "broken",
                "getChunkCount",
                (java.util.function.Function<Object[], Object>) ignored -> {
                    throw new IllegalStateException("expected");
                }
            )
        );
        SnapshotRepository<WorldChunkSnapshot> repository = new SnapshotRepository<>();
        repository.publish(
            new ImmutableSnapshot<>(
                Instant.EPOCH,
                List.of(new WorldChunkSnapshot("broken", 4))
            )
        );
        List<Throwable> failures = new ArrayList<>();
        BukkitChunkSnapshotCapture capture = new BukkitChunkSnapshotCapture(
            proxy(Server.class, Map.of("getWorlds", List.of(broken, good))),
            repository,
            failures::add
        );

        List<WorldChunkSnapshot> result = capture(capture);

        assertEquals(1, failures.size());
        assertEquals(List.of("broken", "good"), result.stream().map(WorldChunkSnapshot::world).toList());
        assertEquals(4, result.getFirst().loadedChunks());
        assertEquals(17, result.getLast().loadedChunks());
    }

    @Test
    void emptyWorldListProducesAnEmptyCompleteSnapshot() {
        BukkitChunkSnapshotCapture capture = new BukkitChunkSnapshotCapture(
            proxy(Server.class, Map.of("getWorlds", List.of())),
            new SnapshotRepository<>(),
            failure -> {
                throw new AssertionError(failure);
            }
        );

        assertTrue(capture(capture).isEmpty());
    }

    private static List<WorldChunkSnapshot> capture(
        BukkitChunkSnapshotCapture capture
    ) {
        AtomicReference<List<WorldChunkSnapshot>> result = new AtomicReference<>();
        capture.capture(new SnapshotCompletion<>() {
            @Override
            public void success(List<WorldChunkSnapshot> values) {
                result.set(values);
            }

            @Override
            public void failure(Throwable failure) {
                throw new AssertionError(failure);
            }
        });
        return result.get();
    }
}
