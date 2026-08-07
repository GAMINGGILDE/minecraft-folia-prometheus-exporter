package de.minecraftgilde.prometheus.minecraft.entity;

import de.minecraftgilde.prometheus.minecraft.WorldLabel;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Complete immutable output of one distributed reconciliation scan. */
record EntityScanResult(
    long runId,
    Map<String, EntityWorldScanStatus> worldStatuses,
    List<EntityObservation> observations,
    Duration duration
) {

    EntityScanResult {
        if (runId < 1L) {
            throw new IllegalArgumentException("runId must be positive");
        }
        worldStatuses = normalizedWorldStatuses(worldStatuses);
        observations = List.copyOf(
            Objects.requireNonNull(observations, "observations")
        );
        duration = Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }

    private static Map<String, EntityWorldScanStatus> normalizedWorldStatuses(
        Map<String, EntityWorldScanStatus> statuses
    ) {
        Objects.requireNonNull(statuses, "worldStatuses");
        Map<String, EntityWorldScanStatus> normalized = new LinkedHashMap<>();
        statuses.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String world = WorldLabel.normalize(entry.getKey());
                EntityWorldScanStatus previous = normalized.put(
                    world,
                    Objects.requireNonNull(entry.getValue(), "world status")
                );
                if (previous != null) {
                    throw new IllegalArgumentException(
                        "Duplicate normalized entity world"
                    );
                }
            });
        return Collections.unmodifiableMap(normalized);
    }
}
