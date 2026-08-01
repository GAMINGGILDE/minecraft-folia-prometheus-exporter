package de.minecraftgilde.prometheus.collector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Registers collectors and coordinates their isolated, deterministic lifecycle. */
public final class CollectorCoordinator {

    private final Map<String, ManagedCollector> collectors = new LinkedHashMap<>();
    private final List<ManagedCollector> startedCollectors = new ArrayList<>();
    private final BiConsumer<String, CollectorState> stateListener;
    private final BiConsumer<String, Throwable> failureListener;
    private boolean registrationClosed;
    private boolean started;

    public CollectorCoordinator(
        BiConsumer<String, CollectorState> stateListener,
        BiConsumer<String, Throwable> failureListener
    ) {
        this.stateListener = Objects.requireNonNull(stateListener, "stateListener");
        this.failureListener = Objects.requireNonNull(
            failureListener,
            "failureListener"
        );
    }

    public synchronized void register(ManagedCollector collector) {
        Objects.requireNonNull(collector, "collector");
        if (registrationClosed) {
            throw new IllegalStateException(
                "Collectors cannot be registered after coordinator startup"
            );
        }
        if (collectors.putIfAbsent(collector.name(), collector) != null) {
            throw new IllegalArgumentException(
                "Duplicate collector name: " + collector.name()
            );
        }
        stateListener.accept(collector.name(), collector.state());
    }

    public synchronized void startAll() {
        if (started) {
            return;
        }

        registrationClosed = true;
        started = true;
        for (ManagedCollector collector : collectors.values()) {
            try {
                collector.start();
                if (collector.state() == CollectorState.RUNNING) {
                    startedCollectors.add(collector);
                }
            } catch (Exception exception) {
                failureListener.accept(collector.name(), exception);
            } finally {
                stateListener.accept(collector.name(), collector.state());
            }
        }
    }

    public synchronized void stopAll() {
        if (!started && startedCollectors.isEmpty()) {
            return;
        }

        List<ManagedCollector> reverseOrder = new ArrayList<>(startedCollectors);
        Collections.reverse(reverseOrder);
        for (ManagedCollector collector : reverseOrder) {
            try {
                collector.stop();
            } catch (Exception exception) {
                failureListener.accept(collector.name(), exception);
            } finally {
                stateListener.accept(collector.name(), collector.state());
            }
        }
        startedCollectors.clear();
        started = false;
    }

    public synchronized Map<String, CollectorState> states() {
        Map<String, CollectorState> states = new LinkedHashMap<>();
        collectors.forEach((name, collector) -> states.put(name, collector.state()));
        return Collections.unmodifiableMap(states);
    }
}
