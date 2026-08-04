package de.minecraftgilde.prometheus.minecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class EntityGroupClassifierTest {

    private final EntityGroupClassifier classifier = new EntityGroupClassifier();

    @ParameterizedTest
    @EnumSource(EntityType.class)
    void everyPinnedEntityTypeHasExactlyOneConservativeResult(EntityType type) {
        EntityGroup group = classifier.classify(type);

        assertNotNull(group);
        assertEquals(1, Stream.of(EntityGroup.values()).filter(group::equals).count());
        assertEquals(type == EntityType.PLAYER, classifier.excluded(type));
    }

    @ParameterizedTest
    @MethodSource("representativeGroups")
    void appliesTheDocumentedPriorityOrder(
        EntityType type,
        EntityGroup expected
    ) {
        assertEquals(expected, classifier.classify(type));
    }

    @Test
    void unknownAndFutureMissingTypeInformationStayOther() {
        assertEquals(EntityGroup.OTHER, classifier.classify(EntityType.UNKNOWN));
        assertEquals(EntityGroup.OTHER, classifier.classify(null));
        assertFalse(classifier.excluded(EntityType.UNKNOWN));
    }

    @Test
    void exactTypeLabelsAreControlledNamespacedKeys() {
        assertEquals("minecraft:zombie", EntityTypeLabel.normalize(EntityType.ZOMBIE));
        assertEquals("unknown", EntityTypeLabel.normalize(EntityType.UNKNOWN));
        assertEquals("unknown", EntityTypeLabel.normalize(null));
        assertTrue(
            Stream.of(EntityType.values())
                .filter(type -> type != EntityType.UNKNOWN)
                .map(EntityTypeLabel::normalize)
                .noneMatch(String::isBlank)
        );
    }

    private static Stream<Arguments> representativeGroups() {
        return Stream.of(
            Arguments.of(EntityType.VILLAGER, EntityGroup.VILLAGER),
            Arguments.of(EntityType.WANDERING_TRADER, EntityGroup.VILLAGER),
            Arguments.of(EntityType.ITEM, EntityGroup.ITEM),
            Arguments.of(EntityType.ARROW, EntityGroup.PROJECTILE),
            Arguments.of(EntityType.OAK_BOAT, EntityGroup.VEHICLE),
            Arguments.of(EntityType.MINECART, EntityGroup.VEHICLE),
            Arguments.of(EntityType.TEXT_DISPLAY, EntityGroup.DISPLAY),
            Arguments.of(EntityType.ZOMBIE, EntityGroup.MONSTER),
            Arguments.of(EntityType.COW, EntityGroup.ANIMAL),
            Arguments.of(EntityType.COD, EntityGroup.WATER),
            Arguments.of(EntityType.BAT, EntityGroup.AMBIENT),
            Arguments.of(EntityType.ARMOR_STAND, EntityGroup.OTHER)
        );
    }
}
