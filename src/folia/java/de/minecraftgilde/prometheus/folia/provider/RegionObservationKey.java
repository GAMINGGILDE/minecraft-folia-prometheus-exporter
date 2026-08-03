package de.minecraftgilde.prometheus.folia.provider;

import de.minecraftgilde.prometheus.minecraft.WorldLabel;

/** Internal anchor identity; coordinates never leave the provider as labels. */
record RegionObservationKey(String world, int chunkX, int chunkZ) {

    RegionObservationKey {
        world = WorldLabel.normalize(world);
    }
}
