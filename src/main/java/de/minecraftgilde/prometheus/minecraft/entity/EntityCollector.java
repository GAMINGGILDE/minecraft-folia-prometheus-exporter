package de.minecraftgilde.prometheus.minecraft.entity;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import de.minecraftgilde.prometheus.collector.AbstractCollector;
import de.minecraftgilde.prometheus.collector.PeriodicSnapshotCollector;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/** Hybrid listener and periodic reconciliation lifecycle for entity gauges. */
final class EntityCollector extends AbstractCollector implements Listener {

    private final EntityEventRegistration registration;
    private final EntityStateStore stateStore;
    private final EntityGroupClassifier classifier;
    private final PeriodicSnapshotCollector<EntityScanResult> reconciliation;
    private final Consumer<Throwable> failureListener;
    private final ReentrantReadWriteLock activityLock =
        new ReentrantReadWriteLock();
    private boolean acceptingEvents;

    EntityCollector(
        boolean enabled,
        EntityEventRegistration registration,
        EntityStateStore stateStore,
        EntityGroupClassifier classifier,
        PeriodicSnapshotCollector<EntityScanResult> reconciliation,
        Consumer<Throwable> failureListener
    ) {
        super("entities", enabled);
        this.registration = Objects.requireNonNull(registration, "registration");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.reconciliation = Objects.requireNonNull(
            reconciliation,
            "reconciliation"
        );
        this.failureListener = Objects.requireNonNull(
            failureListener,
            "failureListener"
        );
    }

    @Override
    protected void startCollector() throws Exception {
        activityLock.writeLock().lock();
        try {
            stateStore.start();
            registration.register(this);
            acceptingEvents = true;
            reconciliation.start();
        } catch (Exception failure) {
            acceptingEvents = false;
            safeUnregister();
            stateStore.stop();
            throw failure;
        } finally {
            activityLock.writeLock().unlock();
        }
    }

    @Override
    protected void stopCollector() throws Exception {
        Exception stopFailure = null;
        try {
            reconciliation.stop();
        } catch (Exception failure) {
            stopFailure = failure;
        }
        activityLock.writeLock().lock();
        try {
            acceptingEvents = false;
            try {
                registration.unregister(this);
            } catch (RuntimeException failure) {
                if (stopFailure == null) {
                    stopFailure = failure;
                } else {
                    stopFailure.addSuppressed(failure);
                }
            } finally {
                stateStore.stop();
            }
        } finally {
            activityLock.writeLock().unlock();
        }
        if (stopFailure != null) {
            throw stopFailure;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAdded(EntityAddToWorldEvent event) {
        Objects.requireNonNull(event, "event");
        record(() -> EntityDescriptor.capture(
            event.getEntity(),
            event.getWorld().getName(),
            classifier
        ).ifPresent(value -> stateStore.recordAdd(
            value.identity(),
            value.descriptor()
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoved(EntityRemoveFromWorldEvent event) {
        Objects.requireNonNull(event, "event");
        record(() -> EntityDescriptor.capture(
            event.getEntity(),
            event.getWorld().getName(),
            classifier
        ).ifPresent(value -> stateStore.recordRemove(
            value.identity(),
            value.descriptor()
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        Objects.requireNonNull(event, "event");
        record(() -> stateStore.recordWorldLoad(event.getWorld().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        Objects.requireNonNull(event, "event");
        if (!event.isCancelled()) {
            record(() -> stateStore.recordWorldUnload(event.getWorld().getName()));
        }
    }

    private void record(Runnable update) {
        activityLock.readLock().lock();
        try {
            if (!acceptingEvents) {
                return;
            }
            try {
                update.run();
            } catch (RuntimeException failure) {
                reportFailure(failure);
            }
        } finally {
            activityLock.readLock().unlock();
        }
    }

    private void reportFailure(RuntimeException failure) {
        try {
            IllegalStateException sanitized = new IllegalStateException(
                "An entity event update failed.",
                failure
            );
            failureListener.accept(sanitized);
        } catch (RuntimeException ignored) {
            // A diagnostic observer must never escape a Minecraft event thread.
        }
    }

    private void safeUnregister() {
        try {
            registration.unregister(this);
        } catch (RuntimeException ignored) {
            // Preserve the original start failure.
        }
    }
}
