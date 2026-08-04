package de.minecraftgilde.prometheus.minecraft.entity;

import de.minecraftgilde.prometheus.minecraft.WorldLabel;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable aggregate entity state for one loaded and captured world. */
public record EntityWorldSnapshot(
    String world,
    Map<EntityGroup, Long> groups,
    long totalEntities,
    long livingEntities,
    long villagers,
    long itemEntities,
    long projectiles,
    Map<String, Long> exactTypes
) {

    public EntityWorldSnapshot {
        world = WorldLabel.normalize(world);
        groups = immutableGroups(groups);
        exactTypes = immutableTypes(exactTypes);
        requireNonNegative("totalEntities", totalEntities);
        requireNonNegative("livingEntities", livingEntities);
        requireNonNegative("villagers", villagers);
        requireNonNegative("itemEntities", itemEntities);
        requireNonNegative("projectiles", projectiles);

        long groupTotal = 0L;
        for (long count : groups.values()) {
            groupTotal = Math.addExact(groupTotal, count);
        }
        if (groupTotal != totalEntities) {
            throw new IllegalArgumentException(
                "Entity total must equal the sum of all fixed groups"
            );
        }
        if (livingEntities > totalEntities) {
            throw new IllegalArgumentException(
                "Living entity count cannot exceed the entity total"
            );
        }
        if (villagers != groups.get(EntityGroup.VILLAGER)) {
            throw new IllegalArgumentException("Villager aggregate is inconsistent");
        }
        if (itemEntities != groups.get(EntityGroup.ITEM)) {
            throw new IllegalArgumentException("Item aggregate is inconsistent");
        }
        if (projectiles != groups.get(EntityGroup.PROJECTILE)) {
            throw new IllegalArgumentException("Projectile aggregate is inconsistent");
        }
    }

    public static EntityWorldSnapshot empty(String world) {
        EnumMap<EntityGroup, Long> groups = new EnumMap<>(EntityGroup.class);
        for (EntityGroup group : EntityGroup.values()) {
            groups.put(group, 0L);
        }
        return new EntityWorldSnapshot(
            world,
            groups,
            0L,
            0L,
            0L,
            0L,
            0L,
            Map.of()
        );
    }

    public long group(EntityGroup group) {
        return groups.get(Objects.requireNonNull(group, "group"));
    }

    private static Map<EntityGroup, Long> immutableGroups(
        Map<EntityGroup, Long> values
    ) {
        Objects.requireNonNull(values, "groups");
        EnumMap<EntityGroup, Long> copy = new EnumMap<>(EntityGroup.class);
        for (EntityGroup group : EntityGroup.values()) {
            Long value = values.get(group);
            if (value == null) {
                throw new IllegalArgumentException(
                    "Missing fixed entity group: " + group.metricValue()
                );
            }
            requireNonNegative("group " + group.metricValue(), value);
            copy.put(group, value);
        }
        if (copy.size() != values.size()) {
            throw new IllegalArgumentException("Unexpected entity group key");
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Long> immutableTypes(Map<String, Long> values) {
        Objects.requireNonNull(values, "exactTypes");
        Map<String, Long> copy = new LinkedHashMap<>();
        values.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String type = Objects.requireNonNull(entry.getKey(), "type");
                long count = Objects.requireNonNull(entry.getValue(), "count");
                if (type.isBlank() || count <= 0L) {
                    throw new IllegalArgumentException(
                        "Exact entity types must have a non-blank name and positive count"
                    );
                }
                copy.put(type, count);
            });
        return Collections.unmodifiableMap(copy);
    }

    private static void requireNonNegative(String name, long value) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
