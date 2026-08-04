package de.minecraftgilde.prometheus.minecraft.entity;

/** Fixed, bounded entity groups exported by Phase 7. */
public enum EntityGroup {
    MONSTER("monster"),
    ANIMAL("animal"),
    AMBIENT("ambient"),
    WATER("water"),
    VILLAGER("villager"),
    ITEM("item"),
    PROJECTILE("projectile"),
    VEHICLE("vehicle"),
    DISPLAY("display"),
    OTHER("other");

    private final String metricValue;

    EntityGroup(String metricValue) {
        this.metricValue = metricValue;
    }

    public String metricValue() {
        return metricValue;
    }
}
