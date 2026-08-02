package de.minecraftgilde.prometheus.minecraft;

import java.util.Objects;

/** Immutable aggregate state for one loaded world. */
public record WorldSnapshot(
    String world,
    int players,
    long timeTicks,
    double borderSizeBlocks,
    WeatherLabel weather,
    DifficultyLabel difficulty,
    EnvironmentLabel environment,
    boolean pvpEnabled
) {

    public WorldSnapshot {
        world = Objects.requireNonNull(world, "world");
        weather = Objects.requireNonNull(weather, "weather");
        difficulty = Objects.requireNonNull(difficulty, "difficulty");
        environment = Objects.requireNonNull(environment, "environment");
    }
}
