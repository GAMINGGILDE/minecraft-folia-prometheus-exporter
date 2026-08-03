package de.minecraftgilde.prometheus.folia.provider;

import de.minecraftgilde.prometheus.snapshot.ImmutableSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricFamilyDescriptor;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Maps exactly one immutable observation snapshot to bounded Folia families. */
final class FoliaRegionMetricsCollector implements MultiCollector {

    private final SnapshotRepository<RegionObservation> repository;
    private final Clock clock;
    private final Duration ttl;
    private final List<TpsWindow> windows;
    private final List<String> statistics;
    private final List<TpsThreshold> thresholds;
    private final MetricFamilyDescriptor observedRegions = gauge(
        "minecraft_folia_observed_regions",
        "Number of currently valid regions observed through configured public anchors in each world.",
        "world"
    );
    private final MetricFamilyDescriptor regionTps = gauge(
        "minecraft_folia_region_tps",
        "TPS distribution across currently valid observed Folia regions.",
        "world",
        "window",
        "stat"
    );
    private final MetricFamilyDescriptor belowTps = gauge(
        "minecraft_folia_regions_below_tps",
        "Number of currently valid observed Folia regions below a configured TPS threshold.",
        "world",
        "window",
        "threshold"
    );
    private final MetricFamilyDescriptor regionsWithPlayers = gauge(
        "minecraft_folia_regions_with_players",
        "Number of currently valid observed Folia regions containing sampled online players.",
        "world"
    );
    private final MetricFamilyDescriptor playersPerRegion = gauge(
        "minecraft_folia_players_per_region",
        "Distribution of sampled online player counts across currently valid observed Folia regions.",
        "world",
        "stat"
    );
    private final MetricFamilyDescriptor snapshotAge = gauge(
        "minecraft_folia_region_snapshot_age_seconds",
        "Age in seconds of the oldest currently valid region observation in each world.",
        "world"
    );
    private final List<MetricFamilyDescriptor> descriptors = List.of(
        observedRegions,
        regionTps,
        belowTps,
        regionsWithPlayers,
        playersPerRegion,
        snapshotAge
    );

    FoliaRegionMetricsCollector(
        SnapshotRepository<RegionObservation> repository,
        Clock clock,
        Duration ttl,
        List<TpsWindow> windows,
        List<String> statistics,
        List<TpsThreshold> thresholds
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.windows = List.copyOf(Objects.requireNonNull(windows, "windows"));
        this.statistics = List.copyOf(
            Objects.requireNonNull(statistics, "statistics")
        );
        this.thresholds = List.copyOf(
            Objects.requireNonNull(thresholds, "thresholds")
        );
    }

    @Override
    public MetricSnapshots collect() {
        Instant now = clock.instant();
        ImmutableSnapshot<RegionObservation> snapshot = repository
            .current()
            .orElse(null);
        List<RegionObservation> valid = snapshot == null
            ? List.of()
            : valid(snapshot.values(), now);
        Map<String, List<RegionObservation>> worlds = groupByWorld(valid);

        MetricSnapshots.Builder result = MetricSnapshots.builder();
        result.metricSnapshot(gaugeSnapshot(observedRegions, observed(worlds)));
        result.metricSnapshot(gaugeSnapshot(regionTps, tps(worlds)));
        result.metricSnapshot(gaugeSnapshot(belowTps, below(worlds)));
        result.metricSnapshot(
            gaugeSnapshot(regionsWithPlayers, withPlayers(worlds))
        );
        result.metricSnapshot(
            gaugeSnapshot(playersPerRegion, playerStatistics(worlds))
        );
        result.metricSnapshot(gaugeSnapshot(snapshotAge, ages(worlds, now)));
        return result.build();
    }

    @Override
    public List<MetricFamilyDescriptor> getMetricFamilyDescriptors() {
        return descriptors;
    }

    private List<RegionObservation> valid(
        List<RegionObservation> observations,
        Instant now
    ) {
        Instant cutoff;
        try {
            cutoff = now.minus(ttl);
        } catch (ArithmeticException overflow) {
            cutoff = Instant.MIN;
        }
        Instant finalCutoff = cutoff;
        return observations
            .stream()
            .filter(value -> !value.observedAt().isBefore(finalCutoff))
            .toList();
    }

    private static Map<String, List<RegionObservation>> groupByWorld(
        List<RegionObservation> observations
    ) {
        Map<String, List<RegionObservation>> result = new TreeMap<>();
        for (RegionObservation observation : observations) {
            result.computeIfAbsent(observation.world(), ignored -> new ArrayList<>())
                .add(observation);
        }
        return result;
    }

    private static List<Value> observed(
        Map<String, List<RegionObservation>> worlds
    ) {
        return worlds.entrySet()
            .stream()
            .map(entry -> new Value(
                entry.getValue().size(),
                Labels.of("world", entry.getKey())
            ))
            .toList();
    }

    private List<Value> tps(Map<String, List<RegionObservation>> worlds) {
        List<Value> result = new ArrayList<>();
        for (Map.Entry<String, List<RegionObservation>> world : worlds.entrySet()) {
            for (TpsWindow window : windows) {
                List<Double> values = world.getValue()
                    .stream()
                    .map(observation -> observation.tps().get(window))
                    .filter(Objects::nonNull)
                    .toList();
                for (
                    Map.Entry<String, Double> statistic : FoliaStatistics
                        .summarize(values, statistics)
                        .entrySet()
                ) {
                    result.add(
                        new Value(
                            statistic.getValue(),
                            Labels.of(
                                "world",
                                world.getKey(),
                                "window",
                                window.label(),
                                "stat",
                                statistic.getKey()
                            )
                        )
                    );
                }
            }
        }
        return result;
    }

    private List<Value> below(Map<String, List<RegionObservation>> worlds) {
        List<Value> result = new ArrayList<>();
        for (Map.Entry<String, List<RegionObservation>> world : worlds.entrySet()) {
            for (TpsWindow window : windows) {
                List<Double> values = world.getValue()
                    .stream()
                    .map(observation -> observation.tps().get(window))
                    .filter(Objects::nonNull)
                    .toList();
                if (values.isEmpty()) {
                    continue;
                }
                for (TpsThreshold threshold : thresholds) {
                    long count = values
                        .stream()
                        .filter(value -> value < threshold.value())
                        .count();
                    result.add(
                        new Value(
                            count,
                            Labels.of(
                                "world",
                                world.getKey(),
                                "window",
                                window.label(),
                                "threshold",
                                threshold.label()
                            )
                        )
                    );
                }
            }
        }
        return result;
    }

    private static List<Value> withPlayers(
        Map<String, List<RegionObservation>> worlds
    ) {
        return worlds.entrySet()
            .stream()
            .map(entry -> new Value(
                entry.getValue().stream().filter(value -> value.players() > 0).count(),
                Labels.of("world", entry.getKey())
            ))
            .toList();
    }

    private List<Value> playerStatistics(
        Map<String, List<RegionObservation>> worlds
    ) {
        List<Value> result = new ArrayList<>();
        for (Map.Entry<String, List<RegionObservation>> world : worlds.entrySet()) {
            List<Double> counts = world.getValue()
                .stream()
                .map(value -> (double) value.players())
                .toList();
            for (
                Map.Entry<String, Double> statistic : FoliaStatistics
                    .summarize(counts, statistics)
                    .entrySet()
            ) {
                result.add(
                    new Value(
                        statistic.getValue(),
                        Labels.of(
                            "world",
                            world.getKey(),
                            "stat",
                            statistic.getKey()
                        )
                    )
                );
            }
        }
        return result;
    }

    private static List<Value> ages(
        Map<String, List<RegionObservation>> worlds,
        Instant now
    ) {
        List<Value> result = new ArrayList<>();
        for (Map.Entry<String, List<RegionObservation>> world : worlds.entrySet()) {
            Instant oldest = world.getValue()
                .stream()
                .map(RegionObservation::observedAt)
                .min(Instant::compareTo)
                .orElseThrow();
            Duration age = Duration.between(oldest, now);
            double seconds = age.isNegative()
                ? 0.0
                : age.toMillis() / 1_000.0;
            result.add(new Value(seconds, Labels.of("world", world.getKey())));
        }
        return result;
    }

    private static GaugeSnapshot gaugeSnapshot(
        MetricFamilyDescriptor descriptor,
        Collection<Value> values
    ) {
        List<GaugeSnapshot.GaugeDataPointSnapshot> points = values
            .stream()
            .map(value -> new GaugeSnapshot.GaugeDataPointSnapshot(
                value.value(),
                value.labels(),
                null
            ))
            .toList();
        return new GaugeSnapshot(descriptor.getMetadata(), points);
    }

    private static MetricFamilyDescriptor gauge(
        String name,
        String help,
        String... labels
    ) {
        return MetricFamilyDescriptor.gauge(name)
            .help(help)
            .labelNames(labels)
            .build();
    }

    private record Value(double value, Labels labels) {}
}
