package de.minecraftgilde.prometheus.minecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.WaterMob;
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

    @ParameterizedTest
    @MethodSource("allUnambiguouslyClassifiedTypes")
    void classifiesEveryTypeCoveredByAPublicCategoryInterface(
        EntityType type,
        EntityGroup expected
    ) {
        assertEquals(expected, classifier.classify(type), type.name());
    }

    @ParameterizedTest
    @MethodSource("explicitSpecialTypes")
    void explicitlyClassifiesSpecialNonPlayerTypes(
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

    private static Stream<Arguments> allUnambiguouslyClassifiedTypes() {
        return Arrays.stream(EntityType.values())
            .filter(type -> type != EntityType.UNKNOWN)
            .map(type -> Arguments.of(type, expectedPublicGroup(type)))
            .filter(arguments -> arguments.get()[1] != null);
    }

    private static EntityGroup expectedPublicGroup(EntityType type) {
        Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass == null) {
            return null;
        }
        if (AbstractVillager.class.isAssignableFrom(entityClass)) {
            return EntityGroup.VILLAGER;
        }
        if (Item.class.isAssignableFrom(entityClass)) {
            return EntityGroup.ITEM;
        }
        if (Projectile.class.isAssignableFrom(entityClass)) {
            return EntityGroup.PROJECTILE;
        }
        if (Vehicle.class.isAssignableFrom(entityClass)) {
            return EntityGroup.VEHICLE;
        }
        if (Display.class.isAssignableFrom(entityClass)) {
            return EntityGroup.DISPLAY;
        }
        if (Enemy.class.isAssignableFrom(entityClass)) {
            return EntityGroup.MONSTER;
        }
        if (WaterMob.class.isAssignableFrom(entityClass)) {
            return EntityGroup.WATER;
        }
        if (Ambient.class.isAssignableFrom(entityClass)) {
            return EntityGroup.AMBIENT;
        }
        if (Animals.class.isAssignableFrom(entityClass)) {
            return EntityGroup.ANIMAL;
        }
        return null;
    }

    private static Stream<Arguments> explicitSpecialTypes() {
        return Stream.of(
            Arguments.of(EntityType.ITEM, EntityGroup.ITEM),
            Arguments.of(EntityType.ARMOR_STAND, EntityGroup.OTHER),
            Arguments.of(EntityType.INTERACTION, EntityGroup.OTHER),
            Arguments.of(EntityType.MARKER, EntityGroup.OTHER),
            Arguments.of(EntityType.BLOCK_DISPLAY, EntityGroup.DISPLAY),
            Arguments.of(EntityType.ITEM_DISPLAY, EntityGroup.DISPLAY),
            Arguments.of(EntityType.TEXT_DISPLAY, EntityGroup.DISPLAY),
            Arguments.of(EntityType.FALLING_BLOCK, EntityGroup.OTHER),
            Arguments.of(EntityType.EXPERIENCE_ORB, EntityGroup.OTHER),
            Arguments.of(EntityType.AREA_EFFECT_CLOUD, EntityGroup.OTHER),
            Arguments.of(EntityType.END_CRYSTAL, EntityGroup.OTHER),
            Arguments.of(EntityType.LEASH_KNOT, EntityGroup.OTHER),
            Arguments.of(EntityType.PAINTING, EntityGroup.OTHER),
            Arguments.of(EntityType.ITEM_FRAME, EntityGroup.OTHER),
            Arguments.of(EntityType.GLOW_ITEM_FRAME, EntityGroup.OTHER)
        );
    }
}
