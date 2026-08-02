package de.minecraftgilde.prometheus.minecraft;

/** Immutable loaded-chunk count for one world. */
public record WorldChunkSnapshot(String world, int loadedChunks) {

    public WorldChunkSnapshot {
        world = WorldLabel.normalize(world);
    }
}
