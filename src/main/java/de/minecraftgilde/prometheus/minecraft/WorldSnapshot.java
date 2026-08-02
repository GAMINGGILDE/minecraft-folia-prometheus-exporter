package de.minecraftgilde.prometheus.minecraft;

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
        world = WorldLabel.normalize(world);
        weather = java.util.Objects.requireNonNull(weather, "weather");
        difficulty = java.util.Objects.requireNonNull(difficulty, "difficulty");
        environment = java.util.Objects.requireNonNull(environment, "environment");
    }
}
