package de.minecraftgilde.prometheus.folia.provider;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable region aggregate with no retained Minecraft live object. */
record RegionObservation(
    RegionObservationKey key,
    Instant observedAt,
    Map<TpsWindow, Double> tps,
    int players
) {

    RegionObservation {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(observedAt, "observedAt");
        tps = Map.copyOf(Objects.requireNonNull(tps, "tps"));
        if (tps.isEmpty()) {
            throw new IllegalArgumentException("At least one TPS window is required");
        }
        for (double value : tps.values()) {
            if (
                !Double.isFinite(value)
                    || value < 0.0
                    || value > TpsWindow.MAX_PUBLIC_TICK_RATE
            ) {
                throw new IllegalArgumentException("Invalid region TPS value");
            }
        }
        if (players < 0) {
            throw new IllegalArgumentException("Region player count must not be negative");
        }
    }

    String world() {
        return key.world();
    }
}
