package de.minecraftgilde.prometheus.minecraft.entity;

import de.minecraftgilde.prometheus.minecraft.WorldLabel;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Complete immutable output of one distributed reconciliation scan. */
record EntityScanResult(
    long runId,
    Set<String> loadedWorlds,
    Set<String> retainedWorlds,
    List<EntityObservation> observations,
    Duration duration
) {

    EntityScanResult {
        if (runId < 1L) {
            throw new IllegalArgumentException("runId must be positive");
        }
        loadedWorlds = normalizedWorlds(loadedWorlds);
        retainedWorlds = normalizedWorlds(retainedWorlds);
        if (!loadedWorlds.containsAll(retainedWorlds)) {
            throw new IllegalArgumentException(
                "Retained worlds must also be loaded worlds"
            );
        }
        observations = List.copyOf(
            Objects.requireNonNull(observations, "observations")
        );
        duration = Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }

    private static Set<String> normalizedWorlds(Set<String> worlds) {
        Objects.requireNonNull(worlds, "worlds");
        TreeSet<String> normalized = new TreeSet<>();
        worlds.forEach(world -> normalized.add(WorldLabel.normalize(world)));
        return Set.copyOf(normalized);
    }
}
