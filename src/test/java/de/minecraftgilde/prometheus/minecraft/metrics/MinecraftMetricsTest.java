package de.minecraftgilde.prometheus.minecraft.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.MetricsCore;
import de.minecraftgilde.prometheus.TestConfigurations;
import de.minecraftgilde.prometheus.minecraft.DifficultyLabel;
import de.minecraftgilde.prometheus.minecraft.EnvironmentLabel;
import de.minecraftgilde.prometheus.minecraft.GameModeLabel;
import de.minecraftgilde.prometheus.minecraft.PluginSnapshot;
import de.minecraftgilde.prometheus.minecraft.ServerSnapshot;
import de.minecraftgilde.prometheus.minecraft.WeatherLabel;
import de.minecraftgilde.prometheus.minecraft.WorldChunkSnapshot;
import de.minecraftgilde.prometheus.minecraft.WorldSizeSnapshot;
import de.minecraftgilde.prometheus.minecraft.WorldSnapshot;
import de.minecraftgilde.prometheus.snapshot.ImmutableSnapshot;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class MinecraftMetricsTest {

    @Test
    void registersEveryEnabledFamilyIdempotentlyWithoutOptionalPluginInfo() {
        PrometheusRegistry registry = new PrometheusRegistry();
        MinecraftMetrics metrics = new MinecraftMetrics(
            registry,
            TestConfigurations.snapshotCollectors(true, true, true, true, false)
        );

        metrics.register();
        List<String> first = expositionNames(registry);
        metrics.register();

        assertEquals(first, expositionNames(registry));
        assertEquals(first.size(), first.stream().distinct().count());
        assertTrue(first.contains("minecraft_server_info"));
        assertTrue(first.contains("minecraft_players_by_gamemode"));
        assertTrue(first.contains("minecraft_world_weather"));
        assertTrue(first.contains("minecraft_world_loaded_chunks"));
        assertTrue(first.contains("minecraft_world_size_bytes"));
        assertFalse(first.contains("minecraft_plugin_info"));
    }

    @Test
    void respectsIndependentCollectorSwitches() {
        PrometheusRegistry registry = new PrometheusRegistry();
        new MinecraftMetrics(
            registry,
            TestConfigurations.snapshotCollectors(false, false, true, false, true)
        ).register();

        List<String> names = expositionNames(registry);
        assertEquals(List.of("minecraft_world_loaded_chunks"), names);
    }

    @Test
    void exposesInfoAndFixedOneHotLabelsFromImmutableSnapshots() {
        PrometheusRegistry registry = new PrometheusRegistry();
        MinecraftMetrics metrics = new MinecraftMetrics(
            registry,
            TestConfigurations.snapshotCollectors(true, true, true, true, true)
        );
        metrics.register();
        metrics.serverRepository().publish(
            new ImmutableSnapshot<>(Instant.EPOCH, List.of(serverSnapshot()))
        );
        metrics.worldRepository().publish(
            new ImmutableSnapshot<>(
                Instant.EPOCH,
                List.of(
                    new WorldSnapshot(
                        "world",
                        2,
                        6000,
                        1000.0,
                        WeatherLabel.RAIN,
                        DifficultyLabel.HARD,
                        EnvironmentLabel.NORMAL,
                        true
                    )
                )
            )
        );
        metrics.chunkRepository().publish(
            new ImmutableSnapshot<>(
                Instant.EPOCH,
                List.of(new WorldChunkSnapshot("world", 12))
            )
        );
        metrics.worldSizeRepository().publish(
            new ImmutableSnapshot<>(
                Instant.EPOCH,
                List.of(new WorldSizeSnapshot("world", 123))
            )
        );

        MetricSnapshot serverInfo = metric(registry, "minecraft_server_info");
        assertInstanceOf(InfoSnapshot.class, serverInfo);
        assertEquals(
            "Paper",
            serverInfo.getDataPoints().getFirst().getLabels().get("implementation")
        );
        MetricSnapshot pluginInfo = metric(registry, "minecraft_plugin_info");
        assertInstanceOf(InfoSnapshot.class, pluginInfo);
        assertEquals(1, pluginInfo.getDataPoints().size());

        GaugeSnapshot weather = assertInstanceOf(
            GaugeSnapshot.class,
            metric(registry, "minecraft_world_weather")
        );
        assertEquals(3, weather.getDataPoints().size());
        assertEquals(
            1.0,
            weather.getDataPoints().stream()
                .filter(point -> "rain".equals(point.getLabels().get("weather")))
                .findFirst()
                .orElseThrow()
                .getValue()
        );
    }

    @Test
    void replacingWorldSnapshotsRemovesStaleLabelSeries() {
        PrometheusRegistry registry = new PrometheusRegistry();
        MinecraftMetrics metrics = new MinecraftMetrics(
            registry,
            TestConfigurations.snapshotCollectors(false, true, false, false, false)
        );
        metrics.register();
        metrics.worldRepository().publish(
            new ImmutableSnapshot<>(Instant.EPOCH, List.of(worldSnapshot("old")))
        );
        assertEquals(
            List.of("old"),
            worldLabels(metric(registry, "minecraft_world_players"))
        );

        metrics.worldRepository().publish(
            new ImmutableSnapshot<>(Instant.EPOCH, List.of(worldSnapshot("new")))
        );

        assertEquals(
            List.of("new"),
            worldLabels(metric(registry, "minecraft_world_players"))
        );
    }

    @Test
    void multipleMetricsCoresOwnIndependentMinecraftRegistrations() {
        try (
            MetricsCore first = new MetricsCore(
                "test",
                "unknown",
                "common",
                (name, failure) -> {}
            );
            MetricsCore second = new MetricsCore(
                "test",
                "unknown",
                "common",
                (name, failure) -> {}
            )
        ) {
            MinecraftMetrics firstMetrics = new MinecraftMetrics(
                first.registry(),
                TestConfigurations.snapshotCollectors(true, true, true, true, false)
            );
            MinecraftMetrics secondMetrics = new MinecraftMetrics(
                second.registry(),
                TestConfigurations.snapshotCollectors(true, true, true, true, false)
            );
            firstMetrics.register();
            secondMetrics.register();

            assertNotSame(first.registry(), second.registry());
            assertTrue(expositionNames(first.registry()).contains("minecraft_server_info"));
            assertTrue(expositionNames(second.registry()).contains("minecraft_server_info"));
        }
    }

    private static ServerSnapshot serverSnapshot() {
        EnumMap<GameModeLabel, Integer> gameModes = new EnumMap<>(
            GameModeLabel.class
        );
        gameModes.put(GameModeLabel.SURVIVAL, 2);
        return new ServerSnapshot(
            "Paper",
            "26.1.2",
            "25",
            5,
            1,
            true,
            false,
            10,
            8,
            2,
            100,
            3,
            1,
            1,
            1,
            gameModes,
            1,
            1,
            0,
            List.of(new PluginSnapshot("Exporter", "1", true))
        );
    }

    private static WorldSnapshot worldSnapshot(String name) {
        return new WorldSnapshot(
            name,
            0,
            0,
            100,
            WeatherLabel.CLEAR,
            DifficultyLabel.NORMAL,
            EnvironmentLabel.NORMAL,
            true
        );
    }

    private static List<String> expositionNames(PrometheusRegistry registry) {
        return registry
            .scrape()
            .stream()
            .map(snapshot -> snapshot.getMetadata().getExpositionBasePrometheusName())
            .sorted()
            .toList();
    }

    private static MetricSnapshot metric(
        PrometheusRegistry registry,
        String expositionName
    ) {
        return registry
            .scrape()
            .stream()
            .filter(snapshot -> snapshot
                .getMetadata()
                .getExpositionBasePrometheusName()
                .equals(expositionName))
            .findFirst()
            .orElseThrow();
    }

    private static List<String> worldLabels(MetricSnapshot snapshot) {
        return snapshot
            .getDataPoints()
            .stream()
            .map(point -> point.getLabels().get("world"))
            .toList();
    }
}
