package de.minecraftgilde.prometheus.minecraft;

import java.util.Locale;
import org.bukkit.GameMode;

/** Fixed label set for aggregated player game modes. */
public enum GameModeLabel {
    SURVIVAL,
    CREATIVE,
    ADVENTURE,
    SPECTATOR;

    public String metricValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GameModeLabel from(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> SURVIVAL;
            case CREATIVE -> CREATIVE;
            case ADVENTURE -> ADVENTURE;
            case SPECTATOR -> SPECTATOR;
        };
    }
}
