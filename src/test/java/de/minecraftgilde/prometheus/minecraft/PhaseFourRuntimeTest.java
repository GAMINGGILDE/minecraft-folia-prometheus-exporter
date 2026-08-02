package de.minecraftgilde.prometheus.minecraft;

import static de.minecraftgilde.prometheus.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.minecraftgilde.prometheus.MetricsCore;
import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.CollectionConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.CollectorsConfiguration;
import de.minecraftgilde.prometheus.scheduler.ManualCollectionScheduler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class PhaseFourRuntimeTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-02T10:00:00Z"),
        ZoneOffset.UTC
    );

    @Test
    void onlyWorldSizeCollectorSchedulesTheDedicatedFilesystemTimeout()
        throws Exception {
        Duration generalTimeout = Duration.ofSeconds(3);
        Duration filesystemTimeout = Duration.ofMinutes(2);
        ExporterConfiguration configuration = filesystemOnlyConfiguration(
            generalTimeout,
            filesystemTimeout
        );
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler(false);
        Server server = emptyServer();
        MetricsCore core = new MetricsCore(
            "test",
            "unknown",
            "common",
            false,
            false,
            (collector, failure) -> {}
        );
        PhaseFourRuntime runtime = new PhaseFourRuntime(
            core,
            server,
            scheduler,
            new WorldSizeCalculator(),
            configuration,
            CLOCK.instant(),
            CLOCK,
            Logger.getAnonymousLogger()
        );

        try {
            core.collectorCoordinator().startAll();
            scheduler.runGlobal();

            assertEquals(
                List.of(
                    generalTimeout,
                    generalTimeout,
                    generalTimeout,
                    filesystemTimeout
                ),
                scheduler.delayedDurations()
            );
        } finally {
            core.collectorCoordinator().stopAll();
            runtime.close();
            core.close();
        }
    }

    private static ExporterConfiguration filesystemOnlyConfiguration(
        Duration timeout,
        Duration filesystemTimeout
    ) {
        ExporterConfiguration defaults = ExporterConfiguration.defaults();
        CollectionConfiguration collection = defaults.collection();
        CollectorsConfiguration collectors = defaults.collectors();
        return new ExporterConfiguration(
            defaults.http(),
            new CollectionConfiguration(
                collection.serverInterval(),
                collection.worldInterval(),
                collection.regionInterval(),
                collection.entityInterval(),
                collection.filesystemInterval(),
                timeout,
                filesystemTimeout
            ),
            new CollectorsConfiguration(
                true,
                collectors.events(),
                true,
                true,
                collectors.entities(),
                collectors.foliaRegions(),
                false,
                false,
                true,
                collectors.exporter(),
                collectors.gameplay(),
                collectors.pluginInfo(),
                collectors.detailedEntityTypes(),
                collectors.commands()
            ),
            defaults.folia(),
            defaults.filesystem(),
            defaults.privacy(),
            defaults.logging()
        );
    }

    private static Server emptyServer() {
        PluginManager pluginManager = proxy(
            PluginManager.class,
            Map.of("getPlugins", new Plugin[0])
        );
        return proxy(
            Server.class,
            Map.ofEntries(
                Map.entry("getName", "Paper"),
                Map.entry("getMinecraftVersion", "26.1.2"),
                Map.entry("getOnlinePlayers", List.of()),
                Map.entry("getOnlineMode", true),
                Map.entry("isHardcore", false),
                Map.entry("getViewDistance", 12),
                Map.entry("getSimulationDistance", 8),
                Map.entry("getMaxPlayers", 100),
                Map.entry("getOfflinePlayers", new OfflinePlayer[0]),
                Map.entry("getWhitelistedPlayers", java.util.Set.of()),
                Map.entry("getBannedPlayers", java.util.Set.of()),
                Map.entry("getOperators", java.util.Set.of()),
                Map.entry("getPluginManager", pluginManager),
                Map.entry("getWorlds", List.of())
            )
        );
    }
}
