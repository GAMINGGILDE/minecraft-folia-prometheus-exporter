package de.minecraftgilde.prometheus.scheduler;

/** A cancellable task owned by a {@link CollectionScheduler}. */
@FunctionalInterface
public interface CollectionTask {

    /** Cancels the task if it has not already completed or been cancelled. */
    void cancel();
}
