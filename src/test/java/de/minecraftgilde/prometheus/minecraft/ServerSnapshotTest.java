package de.minecraftgilde.prometheus.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerSnapshotTest {

    @Test
    void defensivelyCopiesAllCollectionsAndFillsFixedGameModes() {
        EnumMap<GameModeLabel, Integer> gameModes = new EnumMap<>(
            GameModeLabel.class
        );
        gameModes.put(GameModeLabel.SURVIVAL, 2);
        List<PluginSnapshot> plugins = new ArrayList<>(
            List.of(new PluginSnapshot("A", "1", true))
        );
        ServerSnapshot snapshot = snapshot(gameModes, plugins);

        gameModes.put(GameModeLabel.SURVIVAL, 99);
        plugins.clear();

        assertEquals(2, snapshot.playersByGameMode().get(GameModeLabel.SURVIVAL));
        assertEquals(0, snapshot.playersByGameMode().get(GameModeLabel.SPECTATOR));
        assertEquals(1, snapshot.plugins().size());
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.plugins().add(new PluginSnapshot("B", "1", true))
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.playersByGameMode().put(GameModeLabel.CREATIVE, 1)
        );
    }

    private static ServerSnapshot snapshot(
        EnumMap<GameModeLabel, Integer> gameModes,
        List<PluginSnapshot> plugins
    ) {
        return new ServerSnapshot(
            "Paper",
            "26.1.2",
            "25",
            5.0,
            1.0,
            true,
            false,
            10,
            10,
            2,
            20,
            3,
            1,
            1,
            1,
            gameModes,
            1,
            1,
            0,
            plugins
        );
    }
}
