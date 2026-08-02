package de.minecraftgilde.prometheus.collector;

/** Starts one non-blocking capture initiated from the global scheduler. */
@FunctionalInterface
public interface SnapshotCapture<T> {

    void capture(SnapshotCompletion<T> completion);
}
