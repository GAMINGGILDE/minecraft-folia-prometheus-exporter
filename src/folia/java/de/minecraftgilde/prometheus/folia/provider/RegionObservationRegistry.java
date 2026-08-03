package de.minecraftgilde.prometheus.folia.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Thread-safe transactional registry for one complete observation generation. */
final class RegionObservationRegistry {

    private static final Comparator<RegionObservation> ORDER = Comparator
        .comparing(RegionObservation::world)
        .thenComparingInt(value -> value.key().chunkX())
        .thenComparingInt(value -> value.key().chunkZ());

    private final Object lock = new Object();
    private long nextRunId;
    private boolean accepting;
    private ActiveRun activeRun;
    private List<RegionObservation> committed = List.of();

    void start() {
        synchronized (lock) {
            accepting = true;
        }
    }

    OptionalLong beginRun() {
        synchronized (lock) {
            if (!accepting || activeRun != null) {
                return OptionalLong.empty();
            }
            long runId = ++nextRunId;
            activeRun = new ActiveRun(runId);
            return OptionalLong.of(runId);
        }
    }

    boolean update(long runId, RegionObservation observation) {
        Objects.requireNonNull(observation, "observation");
        synchronized (lock) {
            if (
                !accepting
                    || activeRun == null
                    || activeRun.id != runId
            ) {
                return false;
            }
            RegionObservation previous = activeRun.values.get(observation.key());
            if (
                previous != null
                    && observation.observedAt().isBefore(previous.observedAt())
            ) {
                return false;
            }
            activeRun.values.put(observation.key(), observation);
            return true;
        }
    }

    Optional<List<RegionObservation>> completeRun(long runId) {
        synchronized (lock) {
            if (
                !accepting
                    || activeRun == null
                    || activeRun.id != runId
            ) {
                return Optional.empty();
            }
            List<RegionObservation> values = new ArrayList<>(
                activeRun.values.values()
            );
            values.sort(ORDER);
            committed = List.copyOf(values);
            activeRun = null;
            return Optional.of(committed);
        }
    }

    boolean failRun(long runId) {
        synchronized (lock) {
            if (activeRun == null || activeRun.id != runId) {
                return false;
            }
            activeRun = null;
            return true;
        }
    }

    List<RegionObservation> current(Instant now, Duration ttl) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(ttl, "ttl");
        Instant cutoff;
        try {
            cutoff = now.minus(ttl);
        } catch (ArithmeticException overflow) {
            cutoff = Instant.MIN;
        }
        synchronized (lock) {
            Instant finalCutoff = cutoff;
            committed = committed
                .stream()
                .filter(value -> !value.observedAt().isBefore(finalCutoff))
                .toList();
            return committed;
        }
    }

    void stop() {
        synchronized (lock) {
            accepting = false;
            activeRun = null;
        }
    }

    private static final class ActiveRun {

        private final long id;
        private final Map<RegionObservationKey, RegionObservation> values =
            new LinkedHashMap<>();

        private ActiveRun(long id) {
            this.id = id;
        }
    }
}
