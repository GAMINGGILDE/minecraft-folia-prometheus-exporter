package de.minecraftgilde.prometheus.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class LabelMappingTest {

    @Test
    void mapsEveryGameModeToAFixedLowercaseValue() {
        assertEquals("survival", GameModeLabel.from(GameMode.SURVIVAL).metricValue());
        assertEquals("creative", GameModeLabel.from(GameMode.CREATIVE).metricValue());
        assertEquals("adventure", GameModeLabel.from(GameMode.ADVENTURE).metricValue());
        assertEquals("spectator", GameModeLabel.from(GameMode.SPECTATOR).metricValue());
    }

    @Test
    void mapsWeatherWithThunderTakingPrecedence() {
        assertEquals(WeatherLabel.CLEAR, WeatherLabel.from(false, false));
        assertEquals(WeatherLabel.RAIN, WeatherLabel.from(true, false));
        assertEquals(WeatherLabel.THUNDER, WeatherLabel.from(true, true));
        assertEquals(WeatherLabel.THUNDER, WeatherLabel.from(false, true));
    }

    @Test
    void mapsDifficultyAndEnvironmentToDocumentedValues() {
        assertEquals("peaceful", DifficultyLabel.from(Difficulty.PEACEFUL).metricValue());
        assertEquals("easy", DifficultyLabel.from(Difficulty.EASY).metricValue());
        assertEquals("normal", DifficultyLabel.from(Difficulty.NORMAL).metricValue());
        assertEquals("hard", DifficultyLabel.from(Difficulty.HARD).metricValue());
        assertEquals(
            "normal",
            EnvironmentLabel.from(World.Environment.NORMAL).metricValue()
        );
        assertEquals(
            "nether",
            EnvironmentLabel.from(World.Environment.NETHER).metricValue()
        );
        assertEquals(
            "the_end",
            EnvironmentLabel.from(World.Environment.THE_END).metricValue()
        );
    }
}
