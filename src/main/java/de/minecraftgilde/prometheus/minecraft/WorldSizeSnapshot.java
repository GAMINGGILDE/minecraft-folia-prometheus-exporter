package de.minecraftgilde.prometheus.minecraft;

/** Immutable asynchronous filesystem result for one world. */
public record WorldSizeSnapshot(String world, long sizeBytes) {

    public WorldSizeSnapshot {
        world = WorldLabel.normalize(world);
        if (sizeBytes < 0L) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }
}
