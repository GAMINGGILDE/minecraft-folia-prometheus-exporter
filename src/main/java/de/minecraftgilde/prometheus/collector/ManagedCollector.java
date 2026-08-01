package de.minecraftgilde.prometheus.collector;

import java.util.Optional;

/**
 * Lifecycle contract for independently managed collectors.
 *
 * <p>Implementations must make lifecycle operations thread-safe, prevent
 * overlapping starts, and make {@link #stop()} idempotent. New collectors should
 * normally extend {@link AbstractCollector}, which provides these guarantees.
 */
public interface ManagedCollector {

    /** Stable internal name. It is also the bounded collector metric label. */
    String name();

    CollectorState state();

    Optional<Throwable> failureCause();

    void start() throws Exception;

    void stop() throws Exception;
}
