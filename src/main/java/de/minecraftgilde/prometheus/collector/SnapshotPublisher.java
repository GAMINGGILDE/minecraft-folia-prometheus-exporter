package de.minecraftgilde.prometheus.collector;

import java.time.Instant;
import java.util.List;

/** Publishes one fully materialized capture inside the active-run boundary. */
@FunctionalInterface
public interface SnapshotPublisher<T> {

    void publish(Instant capturedAt, List<T> values);
}
