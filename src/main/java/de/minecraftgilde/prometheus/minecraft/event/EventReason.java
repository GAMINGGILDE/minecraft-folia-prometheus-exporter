package de.minecraftgilde.prometheus.minecraft.event;

/** Bounded label values for login denials and player kicks. */
public enum EventReason {
    BANNED("banned"),
    WHITELIST("whitelist"),
    SERVER_FULL("server_full"),
    INVALID_SESSION("invalid_session"),
    IDLE("idle"),
    CONNECTION_LOST("connection_lost"),
    MODERATION("moderation"),
    PLUGIN("plugin"),
    UNKNOWN("unknown");

    private final String metricValue;

    EventReason(String metricValue) {
        this.metricValue = metricValue;
    }

    public String metricValue() {
        return metricValue;
    }
}
