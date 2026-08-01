package de.minecraftgilde.prometheus.collector;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Thread-safe collector lifecycle with isolated initialization and cleanup. */
public abstract class AbstractCollector implements ManagedCollector {

    private static final Pattern NAME_PATTERN = Pattern.compile(
        "[a-z][a-z0-9]*(?:[-_][a-z0-9]+)*"
    );

    private final String name;
    private final boolean enabled;
    private final AtomicReference<CollectorState> state;
    private final AtomicReference<Throwable> failureCause = new AtomicReference<>();
    private final Object lifecycleLock = new Object();
    private boolean initialized;

    protected AbstractCollector(String name, boolean enabled) {
        this.name = validateName(name);
        this.enabled = enabled;
        this.state = new AtomicReference<>(
            enabled ? CollectorState.STOPPED : CollectorState.DISABLED
        );
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final CollectorState state() {
        return state.get();
    }

    @Override
    public final Optional<Throwable> failureCause() {
        return Optional.ofNullable(failureCause.get());
    }

    @Override
    public final void start() throws Exception {
        synchronized (lifecycleLock) {
            CollectorState current = state.get();
            if (
                !enabled
                    || current == CollectorState.RUNNING
                    || current == CollectorState.STARTING
                    || current == CollectorState.UNSUPPORTED
                    || current == CollectorState.FAILED
            ) {
                return;
            }

            failureCause.set(null);
            state.set(CollectorState.STARTING);
            try {
                if (!initialized) {
                    initialize();
                    initialized = true;
                }
                startCollector();
                state.set(CollectorState.RUNNING);
            } catch (UnsupportedCollectorException exception) {
                cleanUpAfterFailedStart(exception);
                state.set(CollectorState.UNSUPPORTED);
            } catch (Exception exception) {
                cleanUpAfterFailedStart(exception);
                failureCause.set(exception);
                state.set(CollectorState.FAILED);
                throw exception;
            }
        }
    }

    @Override
    public final void stop() throws Exception {
        synchronized (lifecycleLock) {
            if (state.get() != CollectorState.RUNNING) {
                return;
            }

            try {
                stopCollector();
                state.set(CollectorState.STOPPED);
            } catch (Exception exception) {
                failureCause.set(exception);
                state.set(CollectorState.FAILED);
                throw exception;
            }
        }
    }

    /** Called once before the first start attempt. */
    protected void initialize() throws Exception {}

    /** Starts collector-owned resources. */
    protected abstract void startCollector() throws Exception;

    /** Stops collector-owned resources. Implementations should also be idempotent. */
    protected abstract void stopCollector() throws Exception;

    private void cleanUpAfterFailedStart(Exception original) {
        try {
            stopCollector();
        } catch (Exception cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private static String validateName(String name) {
        String required = Objects.requireNonNull(name, "name");
        if (!NAME_PATTERN.matcher(required).matches()) {
            throw new IllegalArgumentException(
                "Collector name must be a stable lowercase identifier: " + required
            );
        }
        return required;
    }
}
