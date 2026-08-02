package de.minecraftgilde.prometheus.minecraft;

import java.util.Objects;

/** Shared validation for world labels exported by snapshot and event metrics. */
public final class WorldLabel {

    private WorldLabel() {}

    /**
     * Preserves the public Bukkit world name while rejecting values that cannot
     * identify a real loaded world.
     */
    public static String normalize(String worldName) {
        String value = Objects.requireNonNull(worldName, "worldName");
        if (value.isBlank()) {
            throw new IllegalArgumentException("World name must not be blank");
        }
        return value;
    }
}
