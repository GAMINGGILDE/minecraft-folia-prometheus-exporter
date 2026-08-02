package de.minecraftgilde.prometheus.minecraft.metrics;

import de.minecraftgilde.prometheus.minecraft.GameModeLabel;
import de.minecraftgilde.prometheus.minecraft.PluginSnapshot;
import de.minecraftgilde.prometheus.minecraft.ServerSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricFamilyDescriptor;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/** Maps one immutable server snapshot to all Phase-4 server metric families. */
final class ServerMetricsCollector implements MultiCollector {

    private final SnapshotRepository<ServerSnapshot> repository;
    private final boolean pluginInfoEnabled;
    private final MetricFamilyDescriptor serverInfo = info(
        "minecraft_server_info",
        "Server implementation, Minecraft version, and Java version.",
        "implementation",
        "minecraft_version",
        "java_version"
    );
    private final MetricFamilyDescriptor uptime = gauge(
        "minecraft_server_uptime_seconds",
        "Seconds since this exporter plugin activation."
    );
    private final MetricFamilyDescriptor startTime = gauge(
        "minecraft_server_start_time_seconds",
        "Exporter plugin activation time as Unix seconds."
    );
    private final MetricFamilyDescriptor onlineMode = gauge(
        "minecraft_server_online_mode",
        "Whether the server authenticates players in online mode."
    );
    private final MetricFamilyDescriptor hardcore = gauge(
        "minecraft_server_hardcore",
        "Whether the server is configured for hardcore mode."
    );
    private final MetricFamilyDescriptor viewDistance = gauge(
        "minecraft_server_view_distance_chunks",
        "Configured server view distance in chunks."
    );
    private final MetricFamilyDescriptor simulationDistance = gauge(
        "minecraft_server_simulation_distance_chunks",
        "Configured server simulation distance in chunks."
    );
    private final MetricFamilyDescriptor playersOnline = gauge(
        "minecraft_players_online",
        "Number of online players."
    );
    private final MetricFamilyDescriptor playersMax = gauge(
        "minecraft_players_max",
        "Maximum number of players accepted by the server."
    );
    private final MetricFamilyDescriptor playersKnown = gauge(
        "minecraft_players_known_total",
        "Number of player profiles known to the server."
    );
    private final MetricFamilyDescriptor playersWhitelisted = gauge(
        "minecraft_players_whitelisted",
        "Number of whitelisted player profiles."
    );
    private final MetricFamilyDescriptor playersBanned = gauge(
        "minecraft_players_banned",
        "Number of banned player profiles."
    );
    private final MetricFamilyDescriptor playersOps = gauge(
        "minecraft_players_ops",
        "Number of operator profiles."
    );
    private final MetricFamilyDescriptor playersByGameMode = gauge(
        "minecraft_players_by_gamemode",
        "Online players aggregated by fixed game mode.",
        "gamemode"
    );
    private final MetricFamilyDescriptor pluginsTotal = gauge(
        "minecraft_plugins_total",
        "Number of loaded plugin instances."
    );
    private final MetricFamilyDescriptor pluginsEnabled = gauge(
        "minecraft_plugins_enabled",
        "Number of enabled plugin instances."
    );
    private final MetricFamilyDescriptor pluginsDisabled = gauge(
        "minecraft_plugins_disabled",
        "Number of disabled plugin instances."
    );
    private final MetricFamilyDescriptor pluginInfo = info(
        "minecraft_plugin_info",
        "Optional per-plugin metadata; disabled by default due to label cardinality.",
        "name",
        "version",
        "enabled"
    );
    private final List<MetricFamilyDescriptor> descriptors;

    ServerMetricsCollector(
        SnapshotRepository<ServerSnapshot> repository,
        boolean pluginInfoEnabled
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.pluginInfoEnabled = pluginInfoEnabled;
        List<MetricFamilyDescriptor> values = new ArrayList<>(
            List.of(
                serverInfo,
                uptime,
                startTime,
                onlineMode,
                hardcore,
                viewDistance,
                simulationDistance,
                playersOnline,
                playersMax,
                playersKnown,
                playersWhitelisted,
                playersBanned,
                playersOps,
                playersByGameMode,
                pluginsTotal,
                pluginsEnabled,
                pluginsDisabled
            )
        );
        if (pluginInfoEnabled) {
            values.add(pluginInfo);
        }
        descriptors = List.copyOf(values);
    }

    @Override
    public MetricSnapshots collect() {
        Optional<ServerSnapshot> current = repository
            .current()
            .flatMap(snapshot -> snapshot.values().stream().findFirst());
        MetricSnapshots.Builder result = MetricSnapshots.builder();
        result.metricSnapshot(
            MetricSnapshotFactory.info(
                serverInfo,
                current
                    .map(server -> List.of(
                        Labels.of(
                            "implementation",
                            server.implementation(),
                            "minecraft_version",
                            server.minecraftVersion(),
                            "java_version",
                            server.javaVersion()
                        )
                    ))
                    .orElseGet(List::of)
            )
        );
        addGauge(result, uptime, current, ServerSnapshot::uptimeSeconds);
        addGauge(result, startTime, current, ServerSnapshot::startTimeSeconds);
        addBoolean(result, onlineMode, current, ServerSnapshot::onlineMode);
        addBoolean(result, hardcore, current, ServerSnapshot::hardcore);
        addGauge(result, viewDistance, current, ServerSnapshot::viewDistanceChunks);
        addGauge(
            result,
            simulationDistance,
            current,
            ServerSnapshot::simulationDistanceChunks
        );
        addGauge(result, playersOnline, current, ServerSnapshot::playersOnline);
        addGauge(result, playersMax, current, ServerSnapshot::playersMax);
        addGauge(result, playersKnown, current, ServerSnapshot::playersKnownTotal);
        addGauge(
            result,
            playersWhitelisted,
            current,
            ServerSnapshot::playersWhitelisted
        );
        addGauge(result, playersBanned, current, ServerSnapshot::playersBanned);
        addGauge(result, playersOps, current, ServerSnapshot::playersOps);
        result.metricSnapshot(
            MetricSnapshotFactory.gauge(
                playersByGameMode,
                gameModeValues(current.orElse(null))
            )
        );
        addGauge(result, pluginsTotal, current, ServerSnapshot::pluginsTotal);
        addGauge(result, pluginsEnabled, current, ServerSnapshot::pluginsEnabled);
        addGauge(result, pluginsDisabled, current, ServerSnapshot::pluginsDisabled);
        if (pluginInfoEnabled) {
            result.metricSnapshot(
                MetricSnapshotFactory.info(
                    pluginInfo,
                    current
                        .map(ServerSnapshot::plugins)
                        .orElseGet(List::of)
                        .stream()
                        .map(ServerMetricsCollector::pluginLabels)
                        .toList()
                )
            );
        }
        return result.build();
    }

    @Override
    public List<MetricFamilyDescriptor> getMetricFamilyDescriptors() {
        return descriptors;
    }

    private static void addGauge(
        MetricSnapshots.Builder result,
        MetricFamilyDescriptor descriptor,
        Optional<ServerSnapshot> current,
        ToDoubleFunction<ServerSnapshot> value
    ) {
        result.metricSnapshot(
            MetricSnapshotFactory.gauge(
                descriptor,
                current.isPresent() ? value.applyAsDouble(current.orElseThrow()) : null
            )
        );
    }

    private static void addBoolean(
        MetricSnapshots.Builder result,
        MetricFamilyDescriptor descriptor,
        Optional<ServerSnapshot> current,
        java.util.function.Predicate<ServerSnapshot> value
    ) {
        addGauge(result, descriptor, current, server -> value.test(server) ? 1.0 : 0.0);
    }

    private static List<MetricSnapshotFactory.Value> gameModeValues(
        ServerSnapshot server
    ) {
        if (server == null) {
            return List.of();
        }
        List<MetricSnapshotFactory.Value> result = new ArrayList<>();
        for (GameModeLabel gameMode : GameModeLabel.values()) {
            result.add(
                new MetricSnapshotFactory.Value(
                    server.playersByGameMode().get(gameMode),
                    Labels.of("gamemode", gameMode.metricValue())
                )
            );
        }
        return result;
    }

    private static Labels pluginLabels(PluginSnapshot plugin) {
        return Labels.of(
            "name",
            plugin.name(),
            "version",
            plugin.version(),
            "enabled",
            Boolean.toString(plugin.enabled())
        );
    }

    private static MetricFamilyDescriptor gauge(
        String name,
        String help,
        String... labelNames
    ) {
        return MetricFamilyDescriptor.gauge(name)
            .help(help)
            .labelNames(labelNames)
            .build();
    }

    private static MetricFamilyDescriptor info(
        String name,
        String help,
        String... labelNames
    ) {
        return MetricFamilyDescriptor.info(name)
            .help(help)
            .labelNames(labelNames)
            .build();
    }
}
