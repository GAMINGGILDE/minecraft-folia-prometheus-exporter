package de.minecraftgilde.prometheus.minecraft;

import java.util.Locale;
import org.bukkit.Difficulty;

/** Fixed one-hot difficulty labels. */
public enum DifficultyLabel {
    PEACEFUL,
    EASY,
    NORMAL,
    HARD;

    public String metricValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DifficultyLabel from(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> PEACEFUL;
            case EASY -> EASY;
            case NORMAL -> NORMAL;
            case HARD -> HARD;
        };
    }
}
