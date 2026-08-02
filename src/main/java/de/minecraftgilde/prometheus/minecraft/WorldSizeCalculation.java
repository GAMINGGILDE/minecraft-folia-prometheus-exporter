package de.minecraftgilde.prometheus.minecraft;

import java.io.IOException;
import java.nio.file.Path;

/** Testable calculation boundary for one asynchronous world-directory scan. */
@FunctionalInterface
interface WorldSizeCalculation {

    WorldSizeCalculator.Result calculate(Path worldDirectory) throws IOException;
}
