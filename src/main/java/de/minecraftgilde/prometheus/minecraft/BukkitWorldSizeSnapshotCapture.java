package de.minecraftgilde.prometheus.minecraft;

import de.minecraftgilde.prometheus.collector.SnapshotCapture;
import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import de.minecraftgilde.prometheus.scheduler.CollectionTask;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.bukkit.Server;
import org.bukkit.World;

/** Captures normalized paths globally and scans them through a bounded async queue. */
public final class BukkitWorldSizeSnapshotCapture
    implements SnapshotCapture<WorldSizeSnapshot>, AutoCloseable {

    private final Server server;
    private final CollectionScheduler scheduler;
    private final WorldSizeCalculation calculator;
    private final SnapshotRepository<WorldSizeSnapshot> repository;
    private final Consumer<Throwable> failureListener;
    private final int scanConcurrency;
    private final Object queueLock = new Object();
    private final ArrayDeque<ScanRequest> pendingScans = new ArrayDeque<>();
    private final Set<Path> calculationsInProgress = new HashSet<>();
    private final Set<CaptureRun> runs = new HashSet<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private int activeScans;
    private boolean dispatching;

    public BukkitWorldSizeSnapshotCapture(
        Server server,
        CollectionScheduler scheduler,
        WorldSizeCalculation calculator,
        SnapshotRepository<WorldSizeSnapshot> repository,
        Consumer<Throwable> failureListener,
        int scanConcurrency
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.failureListener = Objects.requireNonNull(
            failureListener,
            "failureListener"
        );
        if (scanConcurrency < 1) {
            throw new IllegalArgumentException("scanConcurrency must be positive");
        }
        this.scanConcurrency = scanConcurrency;
    }

    @Override
    public void capture(SnapshotCompletion<WorldSizeSnapshot> completion) {
        Objects.requireNonNull(completion, "completion");
        if (closed.get() || !completion.isActive()) {
            return;
        }

        final DirectoryCapture directoryCapture;
        try {
            directoryCapture = captureDirectories();
        } catch (Throwable failure) {
            completion.failure(
                new IllegalStateException("World path capture failed", failure)
            );
            return;
        }

        Map<String, WorldSizeSnapshot> previous = previousByWorld();
        Map<String, WorldSizeSnapshot> results = new ConcurrentHashMap<>();
        directoryCapture.failedWorlds().forEach(
            world -> retainPrevious(world, previous, results)
        );
        if (directoryCapture.directories().isEmpty()) {
            if (!closed.get() && completion.isActive()) {
                completion.success(sortedResults(results));
            }
            return;
        }

        CaptureRun run = new CaptureRun(
            completion,
            previous,
            results,
            directoryCapture.directories().size()
        );
        for (WorldDirectory directory : directoryCapture.directories()) {
            run.requests.add(
                new ScanRequest(run, directory.world(), directory.path())
            );
        }
        completion.whenInactive(() -> cancel(run));

        synchronized (queueLock) {
            if (closed.get() || run.cancelled.get() || !completion.isActive()) {
                run.cancelled.set(true);
                return;
            }
            runs.add(run);
            pendingScans.addAll(run.requests);
        }
        dispatch();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        final List<CaptureRun> currentRuns;
        synchronized (queueLock) {
            currentRuns = List.copyOf(runs);
        }
        currentRuns.forEach(this::cancel);
    }

    int calculationsInProgress() {
        synchronized (queueLock) {
            return calculationsInProgress.size();
        }
    }

    int activeScans() {
        synchronized (queueLock) {
            return activeScans;
        }
    }

    int queuedScans() {
        synchronized (queueLock) {
            return pendingScans.size();
        }
    }

    private void dispatch() {
        synchronized (queueLock) {
            if (dispatching) {
                return;
            }
            dispatching = true;
        }

        while (true) {
            final ScanRequest request;
            synchronized (queueLock) {
                if (closed.get() || activeScans >= scanConcurrency) {
                    dispatching = false;
                    return;
                }
                request = nextEligibleRequest();
                if (request == null) {
                    dispatching = false;
                    return;
                }
                request.state = RequestState.SCHEDULING;
                activeScans++;
                calculationsInProgress.add(request.directory);
            }
            schedule(request);
        }
    }

    private ScanRequest nextEligibleRequest() {
        ScanRequest eligible = null;
        Iterator<ScanRequest> iterator = pendingScans.iterator();
        while (iterator.hasNext()) {
            ScanRequest candidate = iterator.next();
            if (
                candidate.state != RequestState.QUEUED
                    || candidate.run.cancelled.get()
                    || !candidate.run.completion.isActive()
            ) {
                iterator.remove();
                candidate.state = RequestState.FINISHED;
                continue;
            }
            if (
                eligible == null
                    && !calculationsInProgress.contains(candidate.directory)
            ) {
                eligible = candidate;
                iterator.remove();
            }
        }
        return eligible;
    }

    private void schedule(ScanRequest request) {
        try {
            CollectionTask task = scheduler.executeAsync(() -> scan(request));
            boolean cancelTask = false;
            boolean released = false;
            synchronized (queueLock) {
                request.task = task;
                if (request.state == RequestState.SCHEDULING) {
                    if (!request.run.acceptsResults()) {
                        request.state = RequestState.FINISHED;
                        releaseReservation(request);
                        cancelTask = true;
                        released = true;
                    } else {
                        request.state = RequestState.SCHEDULED;
                    }
                }
            }
            if (cancelTask) {
                try {
                    task.cancel();
                } catch (RuntimeException cancellationFailure) {
                    reportFailure(cancellationFailure);
                }
            }
            if (released) {
                dispatch();
            }
        } catch (Throwable schedulingFailure) {
            schedulingFailed(request, schedulingFailure);
        }
    }

    private void schedulingFailed(
        ScanRequest request,
        Throwable schedulingFailure
    ) {
        boolean handled = false;
        synchronized (queueLock) {
            if (request.state == RequestState.SCHEDULING) {
                request.state = RequestState.FINISHED;
                releaseReservation(request);
                handled = true;
            }
        }
        if (handled) {
            if (request.run.acceptsResults()) {
                retainPrevious(
                    request.world,
                    request.run.previous,
                    request.run.results
                );
                reportFailure(
                    new IllegalStateException(
                        "Could not schedule world size scan for '"
                            + request.world + "'",
                        schedulingFailure
                    )
                );
            }
            request.run.finishOne();
            dispatch();
        }
    }

    private void scan(ScanRequest request) {
        boolean shouldScan;
        synchronized (queueLock) {
            if (
                request.state != RequestState.SCHEDULING
                    && request.state != RequestState.SCHEDULED
            ) {
                return;
            }
            shouldScan = request.run.acceptsResults();
            if (shouldScan) {
                request.state = RequestState.RUNNING;
            } else {
                request.state = RequestState.FINISHED;
                releaseReservation(request);
            }
        }
        if (!shouldScan) {
            dispatch();
            return;
        }

        WorldSizeCalculator.Result result = null;
        Throwable calculationFailure = null;
        try {
            result = calculator.calculate(request.directory);
        } catch (Throwable failure) {
            calculationFailure = failure;
        }

        if (request.run.acceptsResults()) {
            if (calculationFailure == null) {
                request.run.results.put(
                    request.world,
                    new WorldSizeSnapshot(request.world, result.bytes())
                );
                if (result.skippedEntries() > 0) {
                    reportFailure(
                        new IllegalStateException(
                            "World size for '" + request.world + "' skipped "
                                + result.skippedEntries()
                                + " unreadable or vanished entries"
                        )
                    );
                }
            } else {
                retainPrevious(
                    request.world,
                    request.run.previous,
                    request.run.results
                );
                reportFailure(
                    new IllegalStateException(
                        "Could not calculate world size for '"
                            + request.world + "'",
                        calculationFailure
                    )
                );
            }
        }

        synchronized (queueLock) {
            request.state = RequestState.FINISHED;
            releaseReservation(request);
        }
        request.run.finishOne();
        dispatch();
    }

    private void cancel(CaptureRun run) {
        if (!run.cancelled.compareAndSet(false, true)) {
            return;
        }

        List<CollectionTask> tasksToCancel = new ArrayList<>();
        synchronized (queueLock) {
            runs.remove(run);
            for (ScanRequest request : run.requests) {
                if (request.state == RequestState.QUEUED) {
                    pendingScans.remove(request);
                    request.state = RequestState.FINISHED;
                } else if (request.state == RequestState.SCHEDULED) {
                    request.state = RequestState.FINISHED;
                    releaseReservation(request);
                    if (request.task != null) {
                        tasksToCancel.add(request.task);
                    }
                }
            }
        }
        tasksToCancel.forEach(task -> {
            try {
                task.cancel();
            } catch (RuntimeException cancellationFailure) {
                reportFailure(cancellationFailure);
            }
        });
        dispatch();
    }

    private void releaseReservation(ScanRequest request) {
        activeScans--;
        calculationsInProgress.remove(request.directory);
        if (activeScans < 0) {
            throw new IllegalStateException("World size scan slots became negative");
        }
    }

    private DirectoryCapture captureDirectories() {
        Map<String, Path> directories = new LinkedHashMap<>();
        Set<String> failedWorlds = new LinkedHashSet<>();
        for (World world : List.copyOf(server.getWorlds())) {
            String name = null;
            try {
                name = world.getName();
                if (directories.containsKey(name)) {
                    continue;
                }
                directories.put(
                    name,
                    world.getWorldPath().toAbsolutePath().normalize()
                );
            } catch (Throwable worldFailure) {
                reportFailure(
                    new IllegalStateException(
                        name == null
                            ? "Could not identify one world for size capture"
                            : "Could not capture world path for '" + name + "'",
                        worldFailure
                    )
                );
                if (name != null) {
                    failedWorlds.add(name);
                }
            }
        }
        List<WorldDirectory> sortedDirectories = directories
            .entrySet()
            .stream()
            .map(entry -> new WorldDirectory(entry.getKey(), entry.getValue()))
            .sorted(
                Comparator.comparing(WorldDirectory::world)
                    .thenComparing(directory -> directory.path().toString())
            )
            .toList();
        return new DirectoryCapture(sortedDirectories, failedWorlds);
    }

    private Map<String, WorldSizeSnapshot> previousByWorld() {
        Map<String, WorldSizeSnapshot> result = new HashMap<>();
        repository
            .current()
            .ifPresent(snapshot -> snapshot.values().forEach(
                world -> result.put(world.world(), world)
            ));
        return result;
    }

    private void reportFailure(Throwable failure) {
        try {
            failureListener.accept(failure);
        } catch (RuntimeException ignored) {
            // A reporting failure must never block the scan queue.
        }
    }

    private static List<WorldSizeSnapshot> sortedResults(
        Map<String, WorldSizeSnapshot> results
    ) {
        List<WorldSizeSnapshot> complete = new ArrayList<>(results.values());
        complete.sort(Comparator.comparing(WorldSizeSnapshot::world));
        return complete;
    }

    private static void retainPrevious(
        String world,
        Map<String, WorldSizeSnapshot> previous,
        Map<String, WorldSizeSnapshot> results
    ) {
        WorldSizeSnapshot value = previous.get(world);
        if (value != null) {
            results.put(world, value);
        }
    }

    private final class CaptureRun {

        private final SnapshotCompletion<WorldSizeSnapshot> completion;
        private final Map<String, WorldSizeSnapshot> previous;
        private final Map<String, WorldSizeSnapshot> results;
        private final AtomicInteger remaining;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();
        private final List<ScanRequest> requests = new ArrayList<>();

        private CaptureRun(
            SnapshotCompletion<WorldSizeSnapshot> completion,
            Map<String, WorldSizeSnapshot> previous,
            Map<String, WorldSizeSnapshot> results,
            int scanCount
        ) {
            this.completion = completion;
            this.previous = previous;
            this.results = results;
            remaining = new AtomicInteger(scanCount);
        }

        private boolean acceptsResults() {
            return !closed.get() && !cancelled.get() && completion.isActive();
        }

        private void finishOne() {
            if (
                remaining.decrementAndGet() != 0
                    || !completed.compareAndSet(false, true)
            ) {
                return;
            }
            synchronized (queueLock) {
                runs.remove(this);
            }
            if (acceptsResults()) {
                completion.success(sortedResults(results));
            }
        }
    }

    private final class ScanRequest {

        private final CaptureRun run;
        private final String world;
        private final Path directory;
        private RequestState state = RequestState.QUEUED;
        private CollectionTask task;

        private ScanRequest(CaptureRun run, String world, Path directory) {
            this.run = run;
            this.world = world;
            this.directory = directory;
        }
    }

    private enum RequestState {
        QUEUED,
        SCHEDULING,
        SCHEDULED,
        RUNNING,
        FINISHED
    }

    private record WorldDirectory(String world, Path path) {}

    private record DirectoryCapture(
        List<WorldDirectory> directories,
        Set<String> failedWorlds
    ) {

        private DirectoryCapture {
            directories = List.copyOf(directories);
            failedWorlds = Set.copyOf(failedWorlds);
        }
    }
}
