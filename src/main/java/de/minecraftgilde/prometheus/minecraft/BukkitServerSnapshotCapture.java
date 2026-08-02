package de.minecraftgilde.prometheus.minecraft;

import de.minecraftgilde.prometheus.collector.SnapshotCapture;
import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.scheduler.CollectionScheduler;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Captures global server state and delegates entity-owned reads per player. */
public final class BukkitServerSnapshotCapture
    implements SnapshotCapture<ServerSnapshot> {

    private static final String UNKNOWN_LABEL = "unknown";

    private final Server server;
    private final CollectionScheduler scheduler;
    private final Clock clock;
    private final Instant activationTime;
    private final boolean includePluginInfo;

    public BukkitServerSnapshotCapture(
        Server server,
        CollectionScheduler scheduler,
        Clock clock,
        Instant activationTime,
        boolean includePluginInfo
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.activationTime = Objects.requireNonNull(
            activationTime,
            "activationTime"
        );
        this.includePluginInfo = includePluginInfo;
    }

    @Override
    public void capture(SnapshotCompletion<ServerSnapshot> completion) {
        Objects.requireNonNull(completion, "completion");
        final BaseServerData base;
        final List<? extends Player> onlinePlayers;
        try {
            Instant capturedAt = clock.instant();
            PluginCounts pluginCounts = pluginCounts();
            onlinePlayers = List.copyOf(server.getOnlinePlayers());
            base = new BaseServerData(
                normalized(server.getName()),
                normalized(server.getMinecraftVersion()),
                normalized(System.getProperty("java.version")),
                durationSeconds(Duration.between(activationTime, capturedAt)),
                activationTime.getEpochSecond()
                    + activationTime.getNano() / 1_000_000_000.0,
                server.getOnlineMode(),
                server.isHardcore(),
                server.getViewDistance(),
                server.getSimulationDistance(),
                onlinePlayers.size(),
                server.getMaxPlayers(),
                server.getOfflinePlayers().length,
                server.getWhitelistedPlayers().size(),
                server.getBannedPlayers().size(),
                server.getOperators().size(),
                pluginCounts
            );
        } catch (Throwable failure) {
            completion.failure(sanitizedFailure("Server snapshot capture failed"));
            return;
        }

        if (onlinePlayers.isEmpty()) {
            completion.success(List.of(base.toSnapshot(emptyGameModeCounts())));
            return;
        }

        AtomicIntegerArray gameModes = new AtomicIntegerArray(
            GameModeLabel.values().length
        );
        AtomicInteger remaining = new AtomicInteger(onlinePlayers.size());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();

        Runnable finishOne = () -> {
            if (remaining.decrementAndGet() != 0 || !completed.compareAndSet(false, true)) {
                return;
            }
            Throwable capturedFailure = failure.get();
            if (capturedFailure != null) {
                completion.failure(capturedFailure);
                return;
            }
            EnumMap<GameModeLabel, Integer> counts = new EnumMap<>(
                GameModeLabel.class
            );
            for (GameModeLabel label : GameModeLabel.values()) {
                counts.put(label, gameModes.get(label.ordinal()));
            }
            completion.success(List.of(base.toSnapshot(counts)));
        };

        for (Player player : onlinePlayers) {
            try {
                if (
                    scheduler
                        .executeFor(
                            player,
                            () -> {
                                try {
                                    GameModeLabel label = GameModeLabel.from(
                                        player.getGameMode()
                                    );
                                    gameModes.incrementAndGet(label.ordinal());
                                } catch (Throwable playerFailure) {
                                    failure.compareAndSet(
                                        null,
                                        sanitizedFailure(
                                            "Entity-owned player aggregation failed"
                                        )
                                    );
                                } finally {
                                    finishOne.run();
                                }
                            },
                            finishOne
                        )
                        .isEmpty()
                ) {
                    finishOne.run();
                }
            } catch (Throwable schedulingFailure) {
                failure.compareAndSet(
                    null,
                    sanitizedFailure("Player aggregation scheduling failed")
                );
                finishOne.run();
            }
        }
    }

    private PluginCounts pluginCounts() {
        Set<Plugin> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(unique, server.getPluginManager().getPlugins());
        int enabled = 0;
        Set<PluginSnapshot> info = new LinkedHashSet<>();
        for (Plugin plugin : unique) {
            boolean pluginEnabled = plugin.isEnabled();
            if (pluginEnabled) {
                enabled++;
            }
            if (includePluginInfo) {
                PluginMeta meta = plugin.getPluginMeta();
                info.add(
                    new PluginSnapshot(
                        normalized(meta.getName()),
                        normalized(meta.getVersion()),
                        pluginEnabled
                    )
                );
            }
        }
        List<PluginSnapshot> sortedInfo = new ArrayList<>(info);
        sortedInfo.sort(
            java.util.Comparator.comparing(PluginSnapshot::name)
                .thenComparing(PluginSnapshot::version)
                .thenComparing(PluginSnapshot::enabled)
        );
        return new PluginCounts(
            unique.size(),
            enabled,
            unique.size() - enabled,
            sortedInfo
        );
    }

    private static Map<GameModeLabel, Integer> emptyGameModeCounts() {
        EnumMap<GameModeLabel, Integer> counts = new EnumMap<>(GameModeLabel.class);
        for (GameModeLabel label : GameModeLabel.values()) {
            counts.put(label, 0);
        }
        return counts;
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN_LABEL;
        }
        return value.trim();
    }

    private static double durationSeconds(Duration duration) {
        if (duration.isNegative()) {
            return 0.0;
        }
        return duration.getSeconds() + duration.getNano() / 1_000_000_000.0;
    }

    private static IllegalStateException sanitizedFailure(String message) {
        return new IllegalStateException(message);
    }

    private record PluginCounts(
        int total,
        int enabled,
        int disabled,
        List<PluginSnapshot> info
    ) {}

    private record BaseServerData(
        String implementation,
        String minecraftVersion,
        String javaVersion,
        double uptimeSeconds,
        double startTimeSeconds,
        boolean onlineMode,
        boolean hardcore,
        int viewDistanceChunks,
        int simulationDistanceChunks,
        int playersOnline,
        int playersMax,
        int playersKnownTotal,
        int playersWhitelisted,
        int playersBanned,
        int playersOps,
        PluginCounts pluginCounts
    ) {

        private ServerSnapshot toSnapshot(
            Map<GameModeLabel, Integer> gameModeCounts
        ) {
            return new ServerSnapshot(
                implementation,
                minecraftVersion,
                javaVersion,
                uptimeSeconds,
                startTimeSeconds,
                onlineMode,
                hardcore,
                viewDistanceChunks,
                simulationDistanceChunks,
                playersOnline,
                playersMax,
                playersKnownTotal,
                playersWhitelisted,
                playersBanned,
                playersOps,
                gameModeCounts,
                pluginCounts.total(),
                pluginCounts.enabled(),
                pluginCounts.disabled(),
                pluginCounts.info()
            );
        }
    }
}
