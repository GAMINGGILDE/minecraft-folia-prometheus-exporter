package de.minecraftgilde.prometheus.minecraft.entity;

import de.minecraftgilde.prometheus.minecraft.WorldLabel;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

/** Immutable, identity-free entity values used for aggregation. */
record EntityDescriptor(
    String world,
    EntityGroup group,
    String exactType,
    boolean living
) {

    EntityDescriptor {
        world = WorldLabel.normalize(world);
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(exactType, "exactType");
    }

    static Optional<EntityObservation> observe(
        Entity entity,
        String world,
        EntityGroupClassifier classifier,
        LongSupplier sequence
    ) {
        Optional<Captured> captured = capture(entity, world, classifier);
        return captured.map(value -> new EntityObservation(
            value.identity(),
            value.descriptor(),
            sequence.getAsLong()
        ));
    }

    static Optional<Captured> capture(
        Entity entity,
        String world,
        EntityGroupClassifier classifier
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(classifier, "classifier");
        EntityType type = Objects.requireNonNull(entity.getType(), "entity type");
        if (classifier.excluded(type)) {
            return Optional.empty();
        }
        UUID identity = Objects.requireNonNull(
            entity.getUniqueId(),
            "entity identity"
        );
        return Optional.of(new Captured(
            identity,
            new EntityDescriptor(
                world,
                classifier.classify(type),
                EntityTypeLabel.normalize(type),
                entity instanceof LivingEntity
            )
        ));
    }

    record Captured(UUID identity, EntityDescriptor descriptor) {

        Captured {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(descriptor, "descriptor");
        }
    }
}
