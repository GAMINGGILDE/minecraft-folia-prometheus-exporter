package de.minecraftgilde.prometheus.minecraft.metrics;

import de.minecraftgilde.prometheus.minecraft.DifficultyLabel;
import de.minecraftgilde.prometheus.minecraft.EnvironmentLabel;
import de.minecraftgilde.prometheus.minecraft.WeatherLabel;
import de.minecraftgilde.prometheus.minecraft.WorldSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricFamilyDescriptor;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/** Maps a complete loaded-world snapshot to bounded world metric families. */
final class WorldMetricsCollector implements MultiCollector {

    private final SnapshotRepository<WorldSnapshot> repository;
    private final MetricFamilyDescriptor players = gauge(
        "minecraft_world_players",
        "Online players in each loaded world.",
        "world"
    );
    private final MetricFamilyDescriptor time = gauge(
        "minecraft_world_time_ticks",
        "Relative world time in ticks.",
        "world"
    );
    private final MetricFamilyDescriptor border = gauge(
        "minecraft_world_border_size_blocks",
        "World border diameter in blocks.",
        "world"
    );
    private final MetricFamilyDescriptor weather = gauge(
        "minecraft_world_weather",
        "One-hot current weather using clear, rain, and thunder.",
        "world",
        "weather"
    );
    private final MetricFamilyDescriptor difficulty = gauge(
        "minecraft_world_difficulty",
        "One-hot world difficulty.",
        "world",
        "difficulty"
    );
    private final MetricFamilyDescriptor environment = gauge(
        "minecraft_world_environment",
        "One-hot world environment.",
        "world",
        "environment"
    );
    private final MetricFamilyDescriptor pvp = gauge(
        "minecraft_world_pvp_enabled",
        "Whether PVP is enabled in the world.",
        "world"
    );
    private final List<MetricFamilyDescriptor> descriptors = List.of(
        players,
        time,
        border,
        weather,
        difficulty,
        environment,
        pvp
    );

    WorldMetricsCollector(SnapshotRepository<WorldSnapshot> repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public MetricSnapshots collect() {
        List<WorldSnapshot> worlds = repository
            .current()
            .map(snapshot -> snapshot.values())
            .orElseGet(List::of);
        MetricSnapshots.Builder result = MetricSnapshots.builder();
        result.metricSnapshot(values(players, worlds, WorldSnapshot::players));
        result.metricSnapshot(values(time, worlds, WorldSnapshot::timeTicks));
        result.metricSnapshot(
            values(border, worlds, WorldSnapshot::borderSizeBlocks)
        );
        result.metricSnapshot(
            MetricSnapshotFactory.gauge(weather, weatherValues(worlds))
        );
        result.metricSnapshot(
            MetricSnapshotFactory.gauge(difficulty, difficultyValues(worlds))
        );
        result.metricSnapshot(
            MetricSnapshotFactory.gauge(environment, environmentValues(worlds))
        );
        result.metricSnapshot(
            values(pvp, worlds, world -> world.pvpEnabled() ? 1.0 : 0.0)
        );
        return result.build();
    }

    @Override
    public List<MetricFamilyDescriptor> getMetricFamilyDescriptors() {
        return descriptors;
    }

    private static io.prometheus.metrics.model.snapshots.GaugeSnapshot values(
        MetricFamilyDescriptor descriptor,
        List<WorldSnapshot> worlds,
        ToDoubleFunction<WorldSnapshot> extractor
    ) {
        return MetricSnapshotFactory.gauge(
            descriptor,
            worlds
                .stream()
                .map(world -> new MetricSnapshotFactory.Value(
                    extractor.applyAsDouble(world),
                    Labels.of("world", world.world())
                ))
                .toList()
        );
    }

    private static List<MetricSnapshotFactory.Value> weatherValues(
        List<WorldSnapshot> worlds
    ) {
        List<MetricSnapshotFactory.Value> result = new ArrayList<>();
        for (WorldSnapshot world : worlds) {
            for (WeatherLabel label : WeatherLabel.values()) {
                result.add(
                    oneHot(world.world(), "weather", label.metricValue(), label == world.weather())
                );
            }
        }
        return result;
    }

    private static List<MetricSnapshotFactory.Value> difficultyValues(
        List<WorldSnapshot> worlds
    ) {
        List<MetricSnapshotFactory.Value> result = new ArrayList<>();
        for (WorldSnapshot world : worlds) {
            for (DifficultyLabel label : DifficultyLabel.values()) {
                result.add(
                    oneHot(
                        world.world(),
                        "difficulty",
                        label.metricValue(),
                        label == world.difficulty()
                    )
                );
            }
        }
        return result;
    }

    private static List<MetricSnapshotFactory.Value> environmentValues(
        List<WorldSnapshot> worlds
    ) {
        List<MetricSnapshotFactory.Value> result = new ArrayList<>();
        for (WorldSnapshot world : worlds) {
            for (EnvironmentLabel label : EnvironmentLabel.values()) {
                result.add(
                    oneHot(
                        world.world(),
                        "environment",
                        label.metricValue(),
                        label == world.environment()
                    )
                );
            }
        }
        return result;
    }

    private static MetricSnapshotFactory.Value oneHot(
        String world,
        String stateName,
        String state,
        boolean active
    ) {
        return new MetricSnapshotFactory.Value(
            active ? 1.0 : 0.0,
            Labels.of("world", world, stateName, state)
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
}
