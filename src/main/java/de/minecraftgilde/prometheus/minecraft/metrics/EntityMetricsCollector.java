package de.minecraftgilde.prometheus.minecraft.metrics;

import de.minecraftgilde.prometheus.minecraft.entity.EntityGroup;
import de.minecraftgilde.prometheus.minecraft.entity.EntityWorldSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricFamilyDescriptor;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/** Maps exactly one immutable entity snapshot to the Phase-7 gauge families. */
public final class EntityMetricsCollector implements MultiCollector {

    private final SnapshotRepository<EntityWorldSnapshot> repository;
    private final boolean includeExactTypes;
    private final boolean includeProjectileTotal;
    private final MetricFamilyDescriptor groups = gauge(
        "minecraft_entity_group_count",
        "Loaded non-player entities by world and bounded entity group.",
        "world",
        "group"
    );
    private final MetricFamilyDescriptor total = gauge(
        "minecraft_world_entities",
        "Loaded non-player entities in each captured world.",
        "world"
    );
    private final MetricFamilyDescriptor living = gauge(
        "minecraft_world_living_entities",
        "Loaded living entities excluding players in each captured world.",
        "world"
    );
    private final MetricFamilyDescriptor villagers = gauge(
        "minecraft_world_villagers",
        "Loaded villagers and wandering traders in each captured world.",
        "world"
    );
    private final MetricFamilyDescriptor items = gauge(
        "minecraft_world_item_entities",
        "Loaded dropped item entities in each captured world.",
        "world"
    );
    private final MetricFamilyDescriptor projectiles = gauge(
        "minecraft_world_projectiles",
        "Loaded projectile entities in each captured world.",
        "world"
    );
    private final MetricFamilyDescriptor exactTypes = gauge(
        "minecraft_entities",
        "Loaded non-player entities by controlled namespaced Bukkit entity type.",
        "world",
        "type"
    );
    private final List<MetricFamilyDescriptor> descriptors;

    public EntityMetricsCollector(
        SnapshotRepository<EntityWorldSnapshot> repository,
        boolean includeExactTypes,
        boolean includeProjectileTotal
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.includeExactTypes = includeExactTypes;
        this.includeProjectileTotal = includeProjectileTotal;
        List<MetricFamilyDescriptor> values = new ArrayList<>(List.of(
            groups,
            total,
            living,
            villagers,
            items
        ));
        if (includeProjectileTotal) {
            values.add(projectiles);
        }
        if (includeExactTypes) {
            values.add(exactTypes);
        }
        descriptors = List.copyOf(values);
    }

    @Override
    public MetricSnapshots collect() {
        List<EntityWorldSnapshot> worlds = repository
            .current()
            .map(snapshot -> snapshot.values())
            .orElseGet(List::of);
        MetricSnapshots.Builder result = MetricSnapshots.builder()
            .metricSnapshot(MetricSnapshotFactory.gauge(groups, groupValues(worlds)))
            .metricSnapshot(values(total, worlds, EntityWorldSnapshot::totalEntities))
            .metricSnapshot(values(living, worlds, EntityWorldSnapshot::livingEntities))
            .metricSnapshot(values(villagers, worlds, EntityWorldSnapshot::villagers))
            .metricSnapshot(values(items, worlds, EntityWorldSnapshot::itemEntities));
        if (includeProjectileTotal) {
            result.metricSnapshot(
                values(projectiles, worlds, EntityWorldSnapshot::projectiles)
            );
        }
        if (includeExactTypes) {
            result.metricSnapshot(
                MetricSnapshotFactory.gauge(exactTypes, exactTypeValues(worlds))
            );
        }
        return result.build();
    }

    @Override
    public List<MetricFamilyDescriptor> getMetricFamilyDescriptors() {
        return descriptors;
    }

    private static List<MetricSnapshotFactory.Value> groupValues(
        List<EntityWorldSnapshot> worlds
    ) {
        List<MetricSnapshotFactory.Value> result = new ArrayList<>(
            worlds.size() * EntityGroup.values().length
        );
        for (EntityWorldSnapshot world : worlds) {
            for (EntityGroup group : EntityGroup.values()) {
                result.add(new MetricSnapshotFactory.Value(
                    world.group(group),
                    Labels.of(
                        "world",
                        world.world(),
                        "group",
                        group.metricValue()
                    )
                ));
            }
        }
        return result;
    }

    private static List<MetricSnapshotFactory.Value> exactTypeValues(
        List<EntityWorldSnapshot> worlds
    ) {
        List<MetricSnapshotFactory.Value> result = new ArrayList<>();
        for (EntityWorldSnapshot world : worlds) {
            for (Map.Entry<String, Long> type : world.exactTypes().entrySet()) {
                result.add(new MetricSnapshotFactory.Value(
                    type.getValue(),
                    Labels.of("world", world.world(), "type", type.getKey())
                ));
            }
        }
        return result;
    }

    private static io.prometheus.metrics.model.snapshots.GaugeSnapshot values(
        MetricFamilyDescriptor descriptor,
        List<EntityWorldSnapshot> worlds,
        ToDoubleFunction<EntityWorldSnapshot> extractor
    ) {
        return MetricSnapshotFactory.gauge(
            descriptor,
            worlds.stream()
                .map(world -> new MetricSnapshotFactory.Value(
                    extractor.applyAsDouble(world),
                    Labels.of("world", world.world())
                ))
                .toList()
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
