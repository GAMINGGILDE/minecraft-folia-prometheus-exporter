package de.minecraftgilde.prometheus.snapshot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Fully constructed, immutable values captured at one instant.
 *
 * <p>Snapshot value types must themselves be immutable and must never contain
 * Bukkit, Paper, Folia, or other live Minecraft objects.
 */
public record ImmutableSnapshot<T>(Instant capturedAt, List<T> values) {

    public ImmutableSnapshot {
        Objects.requireNonNull(capturedAt, "capturedAt");
        values = List.copyOf(Objects.requireNonNull(values, "values"));
    }
}
