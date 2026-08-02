package de.minecraftgilde.prometheus.collector;

import java.util.List;
import java.util.Objects;

/** Completes exactly one snapshot capture. Late duplicate calls are ignored. */
public interface SnapshotCompletion<T> {

    void success(List<T> values);

    void failure(Throwable failure);

    /**
     * Returns whether this capture run can still publish a result.
     *
     * <p>Captures with internal queues should check this before starting more work.
     */
    default boolean isActive() {
        return true;
    }

    /**
     * Registers cleanup that runs once this capture can no longer publish.
     *
     * <p>The default keeps simple standalone completions source-compatible.
     */
    default void whenInactive(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
    }
}
