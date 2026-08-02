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
import org.bukkit.Difficulty;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.junit.jupiter.api.Test;

class BukkitWorldSnapshotCaptureTest {

    @Test
    void capturesWorldDataAndRemovesUnloadedWorlds() {
        AtomicReference<List<World>> worlds = new AtomicReference<>(
            List.of(world("world", 2, 6000L, false))
        );
        Server server = proxy(
            Server.class,
            Map.of("getWorlds", (java.util.function.Function<Object[], Object>) ignored -> worlds.get())
        );
        SnapshotRepository<WorldSnapshot> repository = new SnapshotRepository<>();
        BukkitWorldSnapshotCapture capture = new BukkitWorldSnapshotCapture(
            server,
            repository,
            failure -> {
                throw new AssertionError(failure);
            }
        );

        List<WorldSnapshot> first = capture(capture);
        repository.publish(new ImmutableSnapshot<>(Instant.EPOCH, first));
        assertEquals(1, first.size());
        assertEquals(2, first.getFirst().players());
        assertEquals(6000L, first.getFirst().timeTicks());
        assertEquals(WeatherLabel.RAIN, first.getFirst().weather());
        assertEquals(DifficultyLabel.HARD, first.getFirst().difficulty());
        assertEquals(EnvironmentLabel.NORMAL, first.getFirst().environment());
        assertTrue(first.getFirst().pvpEnabled());

        worlds.set(List.of(world("new_world", 0, 1000L, false)));
        List<WorldSnapshot> second = capture(capture);
        assertEquals(List.of("new_world"), second.stream().map(WorldSnapshot::world).toList());

        worlds.set(List.of());
        assertTrue(capture(capture).isEmpty());
    }

    @Test
    void retainsOnlyTheFailedWorldsPreviousValueAndUpdatesOthers() {
        WorldSnapshot oldBroken = snapshot("broken", 1L);
        WorldSnapshot oldHealthy = snapshot("healthy", 2L);
        SnapshotRepository<WorldSnapshot> repository = new SnapshotRepository<>();
        repository.publish(
            new ImmutableSnapshot<>(Instant.EPOCH, List.of(oldBroken, oldHealthy))
        );
        World broken = world("broken", 0, 99L, true);
        World healthy = world("healthy", 0, 42L, false);
        Server server = proxy(
            Server.class,
            Map.of("getWorlds", List.of(broken, healthy))
        );
        List<Throwable> failures = new ArrayList<>();

        List<WorldSnapshot> result = capture(
            new BukkitWorldSnapshotCapture(server, repository, failures::add)
        );

        assertEquals(1, failures.size());
        assertEquals(1L, result.get(0).timeTicks());
        assertEquals(42L, result.get(1).timeTicks());
    }

    private static List<WorldSnapshot> capture(BukkitWorldSnapshotCapture capture) {
        AtomicReference<List<WorldSnapshot>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        capture.capture(new SnapshotCompletion<>() {
            @Override
            public void success(List<WorldSnapshot> values) {
                result.set(values);
            }

            @Override
            public void failure(Throwable value) {
                failure.set(value);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }

    private static World world(
        String name,
        int players,
        long time,
        boolean failTime
    ) {
        WorldBorder border = proxy(WorldBorder.class, Map.of("getSize", 1000.5));
        Map<String, Object> methods = new java.util.HashMap<>();
        methods.put("getName", name);
        methods.put("getPlayerCount", players);
        methods.put(
            "getTime",
            (java.util.function.Function<Object[], Object>) ignored -> {
                if (failTime) {
                    throw new IllegalStateException("expected");
                }
                return time;
            }
        );
        methods.put("getWorldBorder", border);
        methods.put("hasStorm", true);
        methods.put("isThundering", false);
        methods.put("getDifficulty", Difficulty.HARD);
        methods.put("getEnvironment", World.Environment.NORMAL);
        methods.put("getPVP", true);
        return proxy(World.class, methods);
    }

    private static WorldSnapshot snapshot(String name, long time) {
        return new WorldSnapshot(
            name,
            0,
            time,
            1.0,
            WeatherLabel.CLEAR,
            DifficultyLabel.NORMAL,
            EnvironmentLabel.NORMAL,
            true
        );
    }
}
