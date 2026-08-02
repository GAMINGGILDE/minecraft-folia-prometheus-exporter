package de.minecraftgilde.prometheus.minecraft;

import java.util.Objects;

/** Immutable loaded-chunk count for one world. */
public record WorldChunkSnapshot(String world, int loadedChunks) {

    public WorldChunkSnapshot {
        world = Objects.requireNonNull(world, "world");
    }
}
