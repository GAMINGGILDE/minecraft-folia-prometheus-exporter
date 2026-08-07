package de.minecraftgilde.prometheus.minecraft.entity;

/** Reliability of one world's contribution to a reconciliation scan. */
enum EntityWorldScanStatus {
    /** Every selected loaded chunk and entity observation completed. */
    SUCCESS,

    /** At least one selected chunk or entity observation failed locally. */
    PARTIAL,

    /** The world's loaded-chunk topology could not be enumerated. */
    UNAVAILABLE
}
