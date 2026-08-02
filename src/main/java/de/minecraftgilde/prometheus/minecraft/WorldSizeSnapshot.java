package de.minecraftgilde.prometheus.minecraft;

import java.util.Objects;

/** Immutable asynchronous filesystem result for one world. */
public record WorldSizeSnapshot(String world, long sizeBytes) {

    public WorldSizeSnapshot {
        world = Objects.requireNonNull(world, "world");
        if (sizeBytes < 0L) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }
}
