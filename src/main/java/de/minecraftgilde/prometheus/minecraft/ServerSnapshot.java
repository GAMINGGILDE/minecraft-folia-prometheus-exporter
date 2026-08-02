package de.minecraftgilde.prometheus.minecraft;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable aggregate server state without Bukkit or player identities. */
public record ServerSnapshot(
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
    Map<GameModeLabel, Integer> playersByGameMode,
    int pluginsTotal,
    int pluginsEnabled,
    int pluginsDisabled,
    List<PluginSnapshot> plugins
) {

    public ServerSnapshot {
        implementation = Objects.requireNonNull(implementation, "implementation");
        minecraftVersion = Objects.requireNonNull(
            minecraftVersion,
            "minecraftVersion"
        );
        javaVersion = Objects.requireNonNull(javaVersion, "javaVersion");
        EnumMap<GameModeLabel, Integer> gameModes = new EnumMap<>(
            GameModeLabel.class
        );
        gameModes.putAll(
            Objects.requireNonNull(playersByGameMode, "playersByGameMode")
        );
        for (GameModeLabel gameMode : GameModeLabel.values()) {
            gameModes.putIfAbsent(gameMode, 0);
        }
        playersByGameMode = Map.copyOf(gameModes);
        plugins = List.copyOf(Objects.requireNonNull(plugins, "plugins"));
    }
}
