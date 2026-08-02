package de.minecraftgilde.prometheus.scheduler;

import java.time.Duration;
import java.util.Optional;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * Testable abstraction over the public Paper/Folia schedulers.
 *
 * <p>There is deliberately no classic Bukkit scheduler fallback.
 */
public interface CollectionScheduler {

    CollectionTask scheduleGlobalAtFixedRate(Duration interval, Runnable task);

    CollectionTask executeAt(
        World world,
        int chunkX,
        int chunkZ,
        Runnable task
    );

    Optional<CollectionTask> executeFor(
        Entity entity,
        Runnable task,
        Runnable retired
    );

    CollectionTask executeAsync(Runnable task);

    CollectionTask executeAsyncAfter(Duration delay, Runnable task);

    /** Cancels every task owned by this scheduler instance's plugin. */
    void cancelAll();
}
