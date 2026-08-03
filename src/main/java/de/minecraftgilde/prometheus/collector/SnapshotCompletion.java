package de.minecraftgilde.prometheus.collector;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Completes exactly one snapshot capture. Late duplicate calls are ignored. */
public interface SnapshotCompletion<T> {

    void success(List<T> values);

    /**
     * Completes this capture only if it can still publish and obtains the values
     * inside that acceptance boundary.
     *
     * <p>The periodic collector overrides this method so a capture can commit
     * transactional state without racing timeout or stop. Simple completions keep
     * the source-compatible default behavior.
     *
     * @return whether this completion accepted the success attempt
     */
    default boolean successIfActive(Supplier<List<T>> values) {
        Objects.requireNonNull(values, "values");
        if (!isActive()) {
            return false;
        }
        success(values.get());
        return true;
    }

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
