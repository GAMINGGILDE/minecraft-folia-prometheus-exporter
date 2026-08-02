package de.minecraftgilde.prometheus.minecraft;

import static de.minecraftgilde.prometheus.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.collector.SnapshotCompletion;
import de.minecraftgilde.prometheus.scheduler.ManualCollectionScheduler;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class BukkitServerSnapshotCaptureTest {

    @Test
    void capturesServerPlayersAndUniquePluginsWithoutPlayerIdentities() {
        Player survival = proxy(Player.class, Map.of("getGameMode", GameMode.SURVIVAL));
        Player creative = proxy(Player.class, Map.of("getGameMode", GameMode.CREATIVE));
        Plugin first = plugin("First", "  ", true);
        Plugin second = plugin("Second", "2.0", false);
        PluginManager pluginManager = proxy(
            PluginManager.class,
            Map.of("getPlugins", new Plugin[] { first, first, second })
        );
        Server server = server(
            List.of(survival, creative),
            pluginManager,
            3,
            2,
            1,
            1
        );
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        BukkitServerSnapshotCapture capture = new BukkitServerSnapshotCapture(
            server,
            scheduler,
            Clock.fixed(Instant.parse("2026-08-02T10:00:12.500Z"), ZoneOffset.UTC),
            Instant.parse("2026-08-02T10:00:00Z"),
            true
        );

        ServerSnapshot snapshot = capture(capture);

        assertEquals("Paper", snapshot.implementation());
        assertEquals("26.1.2", snapshot.minecraftVersion());
        assertEquals(12.5, snapshot.uptimeSeconds());
        assertEquals(2, snapshot.playersOnline());
        assertEquals(1, snapshot.playersByGameMode().get(GameModeLabel.SURVIVAL));
        assertEquals(1, snapshot.playersByGameMode().get(GameModeLabel.CREATIVE));
        assertEquals(3, snapshot.playersKnownTotal());
        assertEquals(2, snapshot.playersWhitelisted());
        assertEquals(1, snapshot.playersBanned());
        assertEquals(1, snapshot.playersOps());
        assertEquals(2, snapshot.pluginsTotal());
        assertEquals(1, snapshot.pluginsEnabled());
        assertEquals(1, snapshot.pluginsDisabled());
        assertEquals(2, snapshot.plugins().size());
        assertTrue(
            snapshot.plugins().stream().anyMatch(
                plugin -> plugin.name().equals("First")
                    && plugin.version().equals("unknown")
                    && plugin.enabled()
            )
        );
        assertEquals(2, scheduler.entityExecutions());
        assertFalse(snapshot.toString().contains("Alice"));
        assertFalse(snapshot.toString().matches(".*[0-9a-f]{8}-[0-9a-f-]{27,}.*"));
    }

    @Test
    void omitsOptionalPluginLabelsButKeepsAggregateCounts() {
        Plugin plugin = plugin("Only", "1", true);
        PluginManager pluginManager = proxy(
            PluginManager.class,
            Map.of("getPlugins", new Plugin[] { plugin })
        );
        BukkitServerSnapshotCapture capture = new BukkitServerSnapshotCapture(
            server(List.of(), pluginManager, 0, 0, 0, 0),
            new ManualCollectionScheduler(),
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            Instant.EPOCH,
            false
        );

        ServerSnapshot snapshot = capture(capture);

        assertEquals(1, snapshot.pluginsTotal());
        assertTrue(snapshot.plugins().isEmpty());
    }

    private static ServerSnapshot capture(BukkitServerSnapshotCapture capture) {
        AtomicReference<List<ServerSnapshot>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        capture.capture(new SnapshotCompletion<>() {
            @Override
            public void success(List<ServerSnapshot> values) {
                result.set(values);
            }

            @Override
            public void failure(Throwable value) {
                failure.set(value);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get().getFirst();
    }

    private static Server server(
        List<Player> players,
        PluginManager pluginManager,
        int known,
        int whitelisted,
        int banned,
        int ops
    ) {
        return proxy(
            Server.class,
            Map.ofEntries(
                Map.entry("getName", "Paper"),
                Map.entry("getMinecraftVersion", "26.1.2"),
                Map.entry("getOnlinePlayers", players),
                Map.entry("getOnlineMode", true),
                Map.entry("isHardcore", false),
                Map.entry("getViewDistance", 12),
                Map.entry("getSimulationDistance", 8),
                Map.entry("getMaxPlayers", 100),
                Map.entry("getOfflinePlayers", new OfflinePlayer[known]),
                Map.entry("getWhitelistedPlayers", offlinePlayers(whitelisted)),
                Map.entry("getBannedPlayers", offlinePlayers(banned)),
                Map.entry("getOperators", offlinePlayers(ops)),
                Map.entry("getPluginManager", pluginManager)
            )
        );
    }

    private static Set<OfflinePlayer> offlinePlayers(int count) {
        Set<OfflinePlayer> result = new HashSet<>();
        for (int index = 0; index < count; index++) {
            result.add(proxy(OfflinePlayer.class, Map.of()));
        }
        return result;
    }

    private static Plugin plugin(String name, String version, boolean enabled) {
        PluginMeta meta = proxy(
            PluginMeta.class,
            Map.of("getName", name, "getVersion", version)
        );
        return proxy(
            Plugin.class,
            Map.of("getPluginMeta", meta, "isEnabled", enabled)
        );
    }
}
