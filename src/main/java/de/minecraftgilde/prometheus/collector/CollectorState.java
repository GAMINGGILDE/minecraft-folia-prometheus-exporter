package de.minecraftgilde.prometheus.collector;

/** Controlled lifecycle states exposed by managed collectors. */
public enum CollectorState {
    DISABLED,
    STARTING,
    RUNNING,
    UNSUPPORTED,
    FAILED,
    STOPPED;

    /** Stable lowercase value suitable for bounded metrics labels. */
    public String metricValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
