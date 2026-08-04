package de.minecraftgilde.prometheus.minecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityWorldSnapshotTest {

    @Test
    void emptySnapshotContainsAllTenGroupsAndImmutableMaps() {
        EntityWorldSnapshot snapshot = EntityWorldSnapshot.empty("world");

        assertEquals(10, snapshot.groups().size());
        assertEquals(0L, snapshot.totalEntities());
        for (EntityGroup group : EntityGroup.values()) {
            assertEquals(0L, snapshot.group(group));
        }
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.groups().put(EntityGroup.MONSTER, 1L)
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.exactTypes().put("minecraft:zombie", 1L)
        );
    }

    @Test
    void rejectsIncompleteOrInconsistentAggregates() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new EntityWorldSnapshot(
                "world",
                Map.of(EntityGroup.MONSTER, 1L),
                1L,
                1L,
                0L,
                0L,
                0L,
                Map.of()
            )
        );

        EnumMap<EntityGroup, Long> groups = new EnumMap<>(EntityGroup.class);
        for (EntityGroup group : EntityGroup.values()) {
            groups.put(group, group == EntityGroup.MONSTER ? 1L : 0L);
        }
        assertThrows(
            IllegalArgumentException.class,
            () -> new EntityWorldSnapshot(
                "world",
                groups,
                2L,
                1L,
                0L,
                0L,
                0L,
                Map.of()
            )
        );
    }
}
