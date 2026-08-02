package de.minecraftgilde.prometheus.minecraft;

import java.util.Locale;
import org.bukkit.World;

/** Fixed one-hot world environment labels. */
public enum EnvironmentLabel {
    NORMAL,
    NETHER,
    THE_END,
    CUSTOM;

    public String metricValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static EnvironmentLabel from(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> NORMAL;
            case NETHER -> NETHER;
            case THE_END -> THE_END;
            default -> CUSTOM;
        };
    }
}
