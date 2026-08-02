package de.minecraftgilde.prometheus.minecraft.event;

import de.minecraftgilde.prometheus.collector.AbstractCollector;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;

/** Directly records bounded counters on the threads that deliver public events. */
public final class EventCollector extends AbstractCollector implements Listener {

    private final PrometheusRegistry registry;
    private final EventRegistration registration;
    private final Consumer<Throwable> failureListener;
    private final Supplier<List<String>> loadedWorldLabels;
    private final ReentrantReadWriteLock activityLock =
        new ReentrantReadWriteLock();
    private EventMetrics metrics;
    private boolean acceptingEvents;

    EventCollector(
        PrometheusRegistry registry,
        boolean enabled,
        EventRegistration registration,
        Consumer<Throwable> failureListener
    ) {
        this(registry, enabled, registration, failureListener, List::of);
    }

    EventCollector(
        PrometheusRegistry registry,
        boolean enabled,
        EventRegistration registration,
        Consumer<Throwable> failureListener,
        Supplier<List<String>> loadedWorldLabels
    ) {
        super("events", enabled);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.registration = Objects.requireNonNull(registration, "registration");
        this.failureListener = Objects.requireNonNull(
            failureListener,
            "failureListener"
        );
        this.loadedWorldLabels = Objects.requireNonNull(
            loadedWorldLabels,
            "loadedWorldLabels"
        );
    }

    @Override
    protected void initialize() {
        metrics = new EventMetrics(registry);
    }

    @Override
    protected void startCollector() {
        activityLock.writeLock().lock();
        try {
            registration.register(this);
            acceptingEvents = true;
        } finally {
            activityLock.writeLock().unlock();
        }
    }

    @Override
    protected void stopCollector() {
        activityLock.writeLock().lock();
        try {
            acceptingEvents = false;
            registration.unregister(this);
        } finally {
            activityLock.writeLock().unlock();
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        Objects.requireNonNull(event, "event");
        record(metrics -> metrics.recordLogin(event.getResult().name()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Objects.requireNonNull(event, "event");
        record(EventMetrics::recordJoin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Objects.requireNonNull(event, "event");
        record(EventMetrics::recordQuit);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        Objects.requireNonNull(event, "event");
        if (!event.isCancelled()) {
            record(metrics -> metrics.recordKick(event.getCause()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerListPing(ServerListPingEvent event) {
        Objects.requireNonNull(event, "event");
        record(EventMetrics::recordServerListPing);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Objects.requireNonNull(event, "event");
        if (!event.isCancelled()) {
            record(EventMetrics::recordChatMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Objects.requireNonNull(event, "event");
        record(metrics -> metrics.recordChunkLoad(
            event.getWorld().getName(),
            event.isNewChunk()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Objects.requireNonNull(event, "event");
        record(metrics -> metrics.recordChunkUnload(
            event.getWorld().getName()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        Objects.requireNonNull(event, "event");
        record(metrics -> metrics.initializeWorld(event.getWorld().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        Objects.requireNonNull(event, "event");
        initializeLoadedWorlds();
    }

    void initializeLoadedWorlds() {
        record(metrics -> loadedWorldLabels.get().forEach(metrics::initializeWorld));
    }

    private void record(Consumer<EventMetrics> observation) {
        activityLock.readLock().lock();
        try {
            if (!acceptingEvents) {
                return;
            }
            try {
                observation.accept(metrics);
            } catch (RuntimeException failure) {
                reportFailure(failure);
            }
        } finally {
            activityLock.readLock().unlock();
        }
    }

    private void reportFailure(RuntimeException cause) {
        try {
            failureListener.accept(
                new IllegalStateException(
                    "An event metric could not be updated",
                    cause
                )
            );
        } catch (RuntimeException ignored) {
            // An observer must never break a Minecraft event thread.
        }
    }
}
