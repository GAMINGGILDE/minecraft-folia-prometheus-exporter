package de.minecraftgilde.prometheus.minecraft.entity;

import java.util.Objects;
import java.util.UUID;

/** Run-local entity observation; identity is never published or logged. */
record EntityObservation(
    UUID identity,
    EntityDescriptor descriptor,
    long observedSequence
) {

    EntityObservation {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(descriptor, "descriptor");
        if (observedSequence < 0L) {
            throw new IllegalArgumentException("observedSequence must not be negative");
        }
    }
}
