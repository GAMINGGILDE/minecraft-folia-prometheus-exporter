package de.minecraftgilde.prometheus.minecraft.entity;

import de.minecraftgilde.prometheus.minecraft.WorldLabel;
import java.util.Objects;
import java.util.UUID;

/** Ordered event journal entry used only while a reconciliation is active. */
record EntityStateDelta(
    long sequence,
    Kind kind,
    UUID identity,
    EntityDescriptor descriptor,
    String world
) {

    enum Kind {
        ADD,
        REMOVE,
        WORLD_LOAD,
        WORLD_UNLOAD
    }

    EntityStateDelta {
        if (sequence < 1L) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.ADD || kind == Kind.REMOVE) {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(descriptor, "descriptor");
            world = descriptor.world();
        } else {
            world = WorldLabel.normalize(world);
        }
    }

    static EntityStateDelta entity(
        long sequence,
        Kind kind,
        UUID identity,
        EntityDescriptor descriptor
    ) {
        return new EntityStateDelta(
            sequence,
            kind,
            identity,
            descriptor,
            descriptor.world()
        );
    }

    static EntityStateDelta world(long sequence, Kind kind, String world) {
        return new EntityStateDelta(sequence, kind, null, null, world);
    }
}
