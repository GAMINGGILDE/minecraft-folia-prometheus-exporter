package de.minecraftgilde.prometheus.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldSizeCalculatorTest {

    @TempDir
    Path temporaryDirectory;

    private final WorldSizeCalculator calculator = new WorldSizeCalculator();

    @Test
    void recursivelySumsRegularFiles() throws Exception {
        Path world = Files.createDirectory(temporaryDirectory.resolve("world"));
        Files.write(world.resolve("level.dat"), new byte[7]);
        Path region = Files.createDirectory(world.resolve("region"));
        Files.write(region.resolve("r.0.0.mca"), new byte[13]);

        WorldSizeCalculator.Result result = calculator.calculate(world);

        assertEquals(20L, result.bytes());
        assertEquals(0, result.skippedEntries());
    }

    @Test
    void doesNotFollowSymbolicLinksOutsideTheWorld() throws Exception {
        Path world = Files.createDirectory(temporaryDirectory.resolve("world"));
        Path outside = Files.write(
            temporaryDirectory.resolve("outside.dat"),
            new byte[100]
        );
        Files.write(world.resolve("inside.dat"), new byte[5]);
        try {
            Files.createSymbolicLink(world.resolve("outside-link"), outside);
        } catch (IOException | UnsupportedOperationException | SecurityException failure) {
            assumeTrue(false, "Symbolic links are unavailable: " + failure.getMessage());
        }

        assertEquals(5L, calculator.calculate(world).bytes());
    }

    @Test
    void rejectsMissingAndSymlinkRootDirectories() throws Exception {
        assertThrows(
            IOException.class,
            () -> calculator.calculate(temporaryDirectory.resolve("vanished"))
        );

        Path target = Files.createDirectory(temporaryDirectory.resolve("target"));
        Path link = temporaryDirectory.resolve("world-link");
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException failure) {
            assumeTrue(false, "Symbolic links are unavailable: " + failure.getMessage());
        }
        assertThrows(IOException.class, () -> calculator.calculate(link));
    }
}
