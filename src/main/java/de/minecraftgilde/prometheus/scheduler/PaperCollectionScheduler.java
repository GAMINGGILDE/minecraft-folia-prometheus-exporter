package de.minecraftgilde.prometheus.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** Uses only the schedulers shared by the public Paper and Folia APIs. */
public final class PaperCollectionScheduler implements CollectionScheduler {

    private static final long MILLIS_PER_TICK = 50L;

    private final Plugin plugin;
    private final Server server;
    private final Set<TaskHandle> tasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public PaperCollectionScheduler(Plugin plugin, Server server) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public CollectionTask scheduleGlobalAtFixedRate(
        Duration interval,
        Runnable task
    ) {
        Objects.requireNonNull(task, "task");
        TaskHandle handle = newHandle();
        try {
            ScheduledTask scheduled = server
                .getGlobalRegionScheduler()
                .runAtFixedRate(
                    plugin,
                    ignored -> task.run(),
                    1L,
                    toTicks(interval)
                );
            handle.attach(scheduled);
            return handle;
        } catch (RuntimeException exception) {
            handle.finished();
            throw exception;
        }
    }

    @Override
    public CollectionTask executeAt(
        World world,
        int chunkX,
        int chunkZ,
        Runnable task
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        TaskHandle handle = newHandle();
        try {
            ScheduledTask scheduled = server
                .getRegionScheduler()
                .run(
                    plugin,
                    world,
                    chunkX,
                    chunkZ,
                    ignored -> runOneShot(handle, task)
                );
            handle.attach(scheduled);
            return handle;
        } catch (RuntimeException exception) {
            handle.finished();
            throw exception;
        }
    }

    @Override
    public Optional<CollectionTask> executeFor(
        Entity entity,
        Runnable task,
        Runnable retired
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(retired, "retired");
        TaskHandle handle = newHandle();
        try {
            ScheduledTask scheduled = entity
                .getScheduler()
                .run(
                    plugin,
                    ignored -> runOneShot(handle, task),
                    () -> runOneShot(handle, retired)
                );
            if (scheduled == null) {
                handle.finished();
                return Optional.empty();
            }
            handle.attach(scheduled);
            return Optional.of(handle);
        } catch (RuntimeException exception) {
            handle.finished();
            throw exception;
        }
    }

    @Override
    public CollectionTask executeAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        TaskHandle handle = newHandle();
        try {
            ScheduledTask scheduled = server
                .getAsyncScheduler()
                .runNow(plugin, ignored -> runOneShot(handle, task));
            handle.attach(scheduled);
            return handle;
        } catch (RuntimeException exception) {
            handle.finished();
            throw exception;
        }
    }

    @Override
    public CollectionTask executeAsyncAfter(Duration delay, Runnable task) {
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(task, "task");
        TaskHandle handle = newHandle();
        try {
            ScheduledTask scheduled = server
                .getAsyncScheduler()
                .runDelayed(
                    plugin,
                    ignored -> runOneShot(handle, task),
                    delay.toMillis(),
                    TimeUnit.MILLISECONDS
                );
            handle.attach(scheduled);
            return handle;
        } catch (RuntimeException exception) {
            handle.finished();
            throw exception;
        }
    }

    @Override
    public void cancelAll() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        for (TaskHandle task : Set.copyOf(tasks)) {
            task.cancel();
        }
        server.getGlobalRegionScheduler().cancelTasks(plugin);
        server.getAsyncScheduler().cancelTasks(plugin);
    }

    static long toTicks(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        long millis = duration.toMillis();
        if (millis <= 0L) {
            throw new IllegalArgumentException(
                "Global scheduler durations must be at least one millisecond"
            );
        }
        long ticks = millis / MILLIS_PER_TICK;
        if (millis % MILLIS_PER_TICK != 0L) {
            ticks++;
        }
        return Math.max(1L, ticks);
    }

    private TaskHandle newHandle() {
        if (cancelled.get()) {
            throw new IllegalStateException("Collection scheduler is stopped");
        }
        TaskHandle handle = new TaskHandle();
        tasks.add(handle);
        if (cancelled.get()) {
            handle.cancel();
            throw new IllegalStateException("Collection scheduler is stopped");
        }
        return handle;
    }

    private static void runOneShot(TaskHandle handle, Runnable task) {
        try {
            task.run();
        } finally {
            handle.finished();
        }
    }

    private final class TaskHandle implements CollectionTask {

        private final AtomicReference<ScheduledTask> scheduled =
            new AtomicReference<>();
        private final AtomicBoolean finished = new AtomicBoolean();

        private void attach(ScheduledTask task) {
            if (!scheduled.compareAndSet(null, task)) {
                throw new IllegalStateException("Scheduled task was attached twice");
            }
            if (finished.get()) {
                tasks.remove(this);
                task.cancel();
            }
        }

        private void finished() {
            if (finished.compareAndSet(false, true)) {
                tasks.remove(this);
            }
        }

        @Override
        public void cancel() {
            if (finished.compareAndSet(false, true)) {
                tasks.remove(this);
                ScheduledTask task = scheduled.get();
                if (task != null) {
                    task.cancel();
                }
            }
        }
    }
}
