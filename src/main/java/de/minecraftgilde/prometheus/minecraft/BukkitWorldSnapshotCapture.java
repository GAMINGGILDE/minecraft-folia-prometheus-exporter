package de.minecraftgilde.prometheus.minecraft;

import de.minecraftgilde.prometheus.collector.SnapshotCapture;
import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Server;
import org.bukkit.World;

/** Captures global world properties without retaining live world objects. */
public final class BukkitWorldSnapshotCapture
    implements SnapshotCapture<WorldSnapshot> {

    private final Server server;
    private final SnapshotRepository<WorldSnapshot> repository;
    private final Consumer<Throwable> failureListener;

    public BukkitWorldSnapshotCapture(
        Server server,
        SnapshotRepository<WorldSnapshot> repository,
        Consumer<Throwable> failureListener
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.failureListener = Objects.requireNonNull(
            failureListener,
            "failureListener"
        );
    }

    @Override
    public void capture(SnapshotCompletion<WorldSnapshot> completion) {
        Objects.requireNonNull(completion, "completion");
        try {
            Map<String, WorldSnapshot> previous = previousByWorld();
            Map<String, WorldSnapshot> captured = new LinkedHashMap<>();
            for (World world : List.copyOf(server.getWorlds())) {
                String name = null;
                try {
                    name = world.getName();
                    if (captured.containsKey(name)) {
                        continue;
                    }
                    captured.put(
                        name,
                        new WorldSnapshot(
                            name,
                            world.getPlayerCount(),
                            world.getTime(),
                            world.getWorldBorder().getSize(),
                            WeatherLabel.from(
                                world.hasStorm(),
                                world.isThundering()
                            ),
                            DifficultyLabel.from(world.getDifficulty()),
                            EnvironmentLabel.from(world.getEnvironment()),
                            pvpEnabled(world)
                        )
                    );
                } catch (Throwable worldFailure) {
                    failureListener.accept(
                        new IllegalStateException(
                            name == null
                                ? "Could not identify one loaded world"
                                : "Could not capture loaded world '" + name + "'"
                        )
                    );
                    if (name != null) {
                        WorldSnapshot old = previous.get(name);
                        if (old != null) {
                            captured.put(name, old);
                        }
                    }
                }
            }
            List<WorldSnapshot> result = new ArrayList<>(captured.values());
            result.sort(java.util.Comparator.comparing(WorldSnapshot::world));
            completion.success(result);
        } catch (Throwable failure) {
            completion.failure(
                new IllegalStateException("World snapshot capture failed")
            );
        }
    }

    private Map<String, WorldSnapshot> previousByWorld() {
        Map<String, WorldSnapshot> result = new HashMap<>();
        repository
            .current()
            .ifPresent(snapshot -> snapshot.values().forEach(
                world -> result.put(world.world(), world)
            ));
        return result;
    }

    @SuppressWarnings("deprecation")
    private static boolean pvpEnabled(World world) {
        return world.getPVP();
    }
}
