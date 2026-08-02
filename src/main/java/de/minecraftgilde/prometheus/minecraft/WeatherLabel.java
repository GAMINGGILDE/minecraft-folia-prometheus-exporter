package de.minecraftgilde.prometheus.minecraft;

import java.util.Locale;

/** Fixed one-hot weather labels. */
public enum WeatherLabel {
    CLEAR,
    RAIN,
    THUNDER;

    public String metricValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WeatherLabel from(boolean storm, boolean thundering) {
        if (thundering) {
            return THUNDER;
        }
        return storm ? RAIN : CLEAR;
    }
}
