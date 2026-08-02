package de.minecraftgilde.prometheus.collector;

import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import de.minecraftgilde.prometheus.scheduler.CollectionTask;
import de.minecraftgilde.prometheus.snapshot.ImmutableSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Periodic global capture with timeout, overlap prevention, and run-identity guards.
 */
public final class PeriodicSnapshotCollector<T> extends AbstractCollector {

    private final CollectionScheduler scheduler;
    private final Duration interval;
    private final Duration timeout;
    private final SnapshotCapture<T> capture;
    private final SnapshotRepository<T> repository;
    private final Clock clock;
    private final BiConsumer<String, Throwable> failureListener;
    private final AtomicBoolean acceptingResults = new AtomicBoolean();
    private final AtomicReference<CaptureRun> activeRun = new AtomicReference<>();
    private final Object runLock = new Object();
    private CollectionTask periodicTask;

    public PeriodicSnapshotCollector(
        String name,
        boolean enabled,
        CollectionScheduler scheduler,
        Duration interval,
        Duration timeout,
        SnapshotCapture<T> capture,
        SnapshotRepository<T> repository,
        Clock clock,
        BiConsumer<String, Throwable> failureListener
    ) {
        super(name, enabled);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.capture = Objects.requireNonNull(capture, "capture");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.failureListener = Objects.requireNonNull(
            failureListener,
            "failureListener"
        );
    }

    @Override
    protected void startCollector() {
        acceptingResults.set(true);
        periodicTask = scheduler.scheduleGlobalAtFixedRate(
            interval,
            this::beginCapture
        );
    }

    @Override
    protected void stopCollector() {
        acceptingResults.set(false);
        synchronized (runLock) {
            CaptureRun run = activeRun.getAndSet(null);
            if (run != null) {
                run.cancelTimeout();
            }
            CollectionTask task = periodicTask;
            periodicTask = null;
            if (task != null) {
                task.cancel();
            }
        }
    }

    private void beginCapture() {
        synchronized (runLock) {
            if (!acceptingResults.get()) {
                return;
            }
            CaptureRun run = new CaptureRun();
            if (!activeRun.compareAndSet(null, run)) {
                return;
            }

            try {
                run.timeoutTask = scheduler.executeAsyncAfter(
                    timeout,
                    () -> timeOut(run)
                );
                capture.capture(new Completion(run));
            } catch (Throwable failure) {
                finishFailure(run, failure);
            }
        }
    }

    private void timeOut(CaptureRun run) {
        if (activeRun.compareAndSet(run, null)) {
            failureListener.accept(
                name(),
                new TimeoutException(
                    "Collector '" + name() + "' exceeded timeout " + timeout
                )
            );
        }
    }

    private void finishSuccess(CaptureRun run, List<T> values) {
        Objects.requireNonNull(values, "values");
        if (
            acceptingResults.get()
                && activeRun.compareAndSet(run, null)
        ) {
            run.cancelTimeout();
            repository.publish(new ImmutableSnapshot<>(clock.instant(), values));
        }
    }

    private void finishFailure(CaptureRun run, Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        if (activeRun.compareAndSet(run, null)) {
            run.cancelTimeout();
            if (acceptingResults.get()) {
                failureListener.accept(name(), failure);
            }
        }
    }

    private final class Completion implements SnapshotCompletion<T> {

        private final CaptureRun run;

        private Completion(CaptureRun run) {
            this.run = run;
        }

        @Override
        public void success(List<T> values) {
            finishSuccess(run, values);
        }

        @Override
        public void failure(Throwable failure) {
            finishFailure(run, failure);
        }
    }

    private final class CaptureRun {

        private volatile CollectionTask timeoutTask;

        private void cancelTimeout() {
            CollectionTask task = timeoutTask;
            if (task != null) {
                task.cancel();
            }
        }
    }
}
