package de.minecraftgilde.prometheus.scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** Deterministic test scheduler; no Minecraft methods are invoked. */
public final class ManualCollectionScheduler implements CollectionScheduler {

    private final List<TestTask> global = new ArrayList<>();
    private final List<TestTask> async = new ArrayList<>();
    private final List<TestTask> delayed = new ArrayList<>();
    private boolean runAsyncImmediately;
    private int entityExecutions;

    public ManualCollectionScheduler() {
        this(true);
    }

    public ManualCollectionScheduler(boolean runAsyncImmediately) {
        this.runAsyncImmediately = runAsyncImmediately;
    }

    @Override
    public CollectionTask scheduleGlobalAtFixedRate(
        Duration interval,
        Runnable task
    ) {
        TestTask scheduled = new TestTask(task, true);
        global.add(scheduled);
        return scheduled;
    }

    @Override
    public CollectionTask executeAt(
        World world,
        int chunkX,
        int chunkZ,
        Runnable task
    ) {
        TestTask scheduled = new TestTask(task, false);
        scheduled.run();
        return scheduled;
    }

    @Override
    public Optional<CollectionTask> executeFor(
        Entity entity,
        Runnable task,
        Runnable retired
    ) {
        entityExecutions++;
        TestTask scheduled = new TestTask(task, false);
        scheduled.run();
        return Optional.of(scheduled);
    }

    @Override
    public CollectionTask executeAsync(Runnable task) {
        TestTask scheduled = new TestTask(task, false);
        async.add(scheduled);
        if (runAsyncImmediately) {
            scheduled.run();
        }
        return scheduled;
    }

    @Override
    public CollectionTask executeAsyncAfter(Duration delay, Runnable task) {
        TestTask scheduled = new TestTask(task, false);
        delayed.add(scheduled);
        return scheduled;
    }

    @Override
    public void cancelAll() {
        global.forEach(TestTask::cancel);
        async.forEach(TestTask::cancel);
        delayed.forEach(TestTask::cancel);
    }

    public void runGlobal() {
        List.copyOf(global).forEach(TestTask::run);
    }

    public void runAsync() {
        List.copyOf(async).forEach(TestTask::run);
    }

    public void runDelayed() {
        List.copyOf(delayed).forEach(TestTask::run);
    }

    public int activeGlobalTasks() {
        return (int) global.stream().filter(task -> !task.cancelled).count();
    }

    public int queuedAsyncTasks() {
        return (int) async.stream().filter(task -> !task.completed).count();
    }

    public int entityExecutions() {
        return entityExecutions;
    }

    public void setRunAsyncImmediately(boolean value) {
        runAsyncImmediately = value;
    }

    private static final class TestTask implements CollectionTask {

        private final Runnable runnable;
        private final boolean repeating;
        private boolean cancelled;
        private boolean completed;

        private TestTask(Runnable runnable, boolean repeating) {
            this.runnable = runnable;
            this.repeating = repeating;
        }

        private void run() {
            if (!cancelled && (repeating || !completed)) {
                completed = !repeating;
                runnable.run();
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
