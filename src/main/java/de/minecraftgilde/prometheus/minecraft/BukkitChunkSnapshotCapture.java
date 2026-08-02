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

/** Uses Paper's public aggregate chunk count and never exposes Chunk objects. */
public final class BukkitChunkSnapshotCapture
    implements SnapshotCapture<WorldChunkSnapshot> {

    private final Server server;
    private final SnapshotRepository<WorldChunkSnapshot> repository;
    private final Consumer<Throwable> failureListener;

    public BukkitChunkSnapshotCapture(
        Server server,
        SnapshotRepository<WorldChunkSnapshot> repository,
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
    public void capture(SnapshotCompletion<WorldChunkSnapshot> completion) {
        Objects.requireNonNull(completion, "completion");
        try {
            Map<String, WorldChunkSnapshot> previous = previousByWorld();
            Map<String, WorldChunkSnapshot> captured = new LinkedHashMap<>();
            for (World world : List.copyOf(server.getWorlds())) {
                String name = null;
                try {
                    name = world.getName();
                    if (captured.containsKey(name)) {
                        continue;
                    }
                    captured.put(
                        name,
                        new WorldChunkSnapshot(name, world.getChunkCount())
                    );
                } catch (Throwable worldFailure) {
                    failureListener.accept(
                        new IllegalStateException(
                            name == null
                                ? "Could not identify one world for chunk capture"
                                : "Could not capture chunk count for world '"
                                    + name + "'"
                        )
                    );
                    if (name != null) {
                        WorldChunkSnapshot old = previous.get(name);
                        if (old != null) {
                            captured.put(name, old);
                        }
                    }
                }
            }
            List<WorldChunkSnapshot> result = new ArrayList<>(captured.values());
            result.sort(java.util.Comparator.comparing(WorldChunkSnapshot::world));
            completion.success(result);
        } catch (Throwable failure) {
            completion.failure(
                new IllegalStateException("Chunk snapshot capture failed")
            );
        }
    }

    private Map<String, WorldChunkSnapshot> previousByWorld() {
        Map<String, WorldChunkSnapshot> result = new HashMap<>();
        repository
            .current()
            .ifPresent(snapshot -> snapshot.values().forEach(
                world -> result.put(world.world(), world)
            ));
        return result;
    }
}
