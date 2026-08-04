package de.minecraftgilde.prometheus.minecraft.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.minecraft.entity.EntityGroup;
import de.minecraftgilde.prometheus.minecraft.entity.EntityWorldSnapshot;
import de.minecraftgilde.prometheus.snapshot.ImmutableSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityMetricsCollectorTest {

    @Test
    void standardRegistrationExportsEveryGroupAndOmitsOptionalFamilies() {
        PrometheusRegistry registry = new PrometheusRegistry();
        SnapshotRepository<EntityWorldSnapshot> repository =
            new SnapshotRepository<>();
        registry.register(new EntityMetricsCollector(repository, false, false));
        repository.publish(new ImmutableSnapshot<>(
            Instant.EPOCH,
            List.of(snapshot("world", 2L, Map.of()))
        ));

        List<String> names = names(registry);
        assertTrue(names.contains("minecraft_entity_group_count"));
        assertTrue(names.contains("minecraft_world_entities"));
        assertTrue(names.contains("minecraft_world_living_entities"));
        assertTrue(names.contains("minecraft_world_villagers"));
        assertTrue(names.contains("minecraft_world_item_entities"));
        assertFalse(names.contains("minecraft_world_projectiles"));
        assertFalse(names.contains("minecraft_entities"));

        MetricSnapshot groups = metric(registry, "minecraft_entity_group_count");
        assertEquals(10, groups.getDataPoints().size());
        assertEquals(
            EntityGroup.values().length,
            groups.getDataPoints().stream()
                .map(point -> point.getLabels().get("group"))
                .distinct()
                .count()
        );
        assertTrue(
            groups.getDataPoints().stream()
                .allMatch(point -> "world".equals(point.getLabels().get("world")))
        );
    }

    @Test
    void optionalExactTypesAndProjectileTotalAppearOnlyWhenEnabled() {
        PrometheusRegistry registry = new PrometheusRegistry();
        SnapshotRepository<EntityWorldSnapshot> repository =
            new SnapshotRepository<>();
        registry.register(new EntityMetricsCollector(repository, true, true));
        repository.publish(new ImmutableSnapshot<>(
            Instant.EPOCH,
            List.of(snapshot(
                "world",
                2L,
                Map.of("minecraft:zombie", 2L)
            ))
        ));

        assertTrue(names(registry).contains("minecraft_entities"));
        assertTrue(names(registry).contains("minecraft_world_projectiles"));
        assertEquals(
            "minecraft:zombie",
            metric(registry, "minecraft_entities")
                .getDataPoints()
                .getFirst()
                .getLabels()
                .get("type")
        );
    }

    @Test
    void replacementAndSuccessfulEmptySnapshotRemoveStaleSeries() {
        PrometheusRegistry registry = new PrometheusRegistry();
        SnapshotRepository<EntityWorldSnapshot> repository =
            new SnapshotRepository<>();
        registry.register(new EntityMetricsCollector(repository, true, false));
        repository.publish(new ImmutableSnapshot<>(
            Instant.EPOCH,
            List.of(snapshot(
                "old",
                1L,
                Map.of("minecraft:zombie", 1L)
            ))
        ));
        assertEquals(
            "old",
            metric(registry, "minecraft_entities")
                .getDataPoints()
                .getFirst()
                .getLabels()
                .get("world")
        );

        repository.publish(new ImmutableSnapshot<>(Instant.EPOCH, List.of()));

        assertTrue(
            metric(registry, "minecraft_entities").getDataPoints().isEmpty()
        );
        assertTrue(
            metric(registry, "minecraft_entity_group_count")
                .getDataPoints()
                .isEmpty()
        );
    }

    private static EntityWorldSnapshot snapshot(
        String world,
        long monsters,
        Map<String, Long> exactTypes
    ) {
        EnumMap<EntityGroup, Long> groups = new EnumMap<>(EntityGroup.class);
        for (EntityGroup group : EntityGroup.values()) {
            groups.put(group, group == EntityGroup.MONSTER ? monsters : 0L);
        }
        return new EntityWorldSnapshot(
            world,
            groups,
            monsters,
            monsters,
            0L,
            0L,
            0L,
            exactTypes
        );
    }

    private static List<String> names(PrometheusRegistry registry) {
        return registry.scrape()
            .stream()
            .map(snapshot -> snapshot
                .getMetadata()
                .getExpositionBasePrometheusName())
            .toList();
    }

    private static MetricSnapshot metric(
        PrometheusRegistry registry,
        String name
    ) {
        return registry.scrape()
            .stream()
            .filter(snapshot -> snapshot
                .getMetadata()
                .getExpositionBasePrometheusName()
                .equals(name))
            .findFirst()
            .orElseThrow();
    }
}
