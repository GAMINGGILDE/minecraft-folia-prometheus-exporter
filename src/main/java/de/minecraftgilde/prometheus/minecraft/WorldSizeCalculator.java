package de.minecraftgilde.prometheus.minecraft;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Best-effort recursive regular-file sum without following symbolic links. */
public final class WorldSizeCalculator implements WorldSizeCalculation {

    @Override
    public Result calculate(Path worldDirectory) throws IOException {
        Path root = Objects.requireNonNull(worldDirectory, "worldDirectory")
            .toAbsolutePath()
            .normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                "World directory is missing, unreadable, or a symbolic link: " + root
            );
        }

        AtomicLong bytes = new AtomicLong();
        AtomicInteger skippedEntries = new AtomicInteger();
        Files.walkFileTree(
            root,
            EnumSet.noneOf(FileVisitOption.class),
            Integer.MAX_VALUE,
            new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes
                ) {
                    if (!inside(root, directory)) {
                        skippedEntries.incrementAndGet();
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attributes
                ) throws IOException {
                    if (inside(root, file) && attributes.isRegularFile()) {
                        try {
                            bytes.set(Math.addExact(bytes.get(), attributes.size()));
                        } catch (ArithmeticException overflow) {
                            throw new IOException(
                                "World size exceeds the supported long range",
                                overflow
                            );
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException failure) {
                    skippedEntries.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(
                    Path directory,
                    IOException failure
                ) {
                    if (failure != null) {
                        skippedEntries.incrementAndGet();
                    }
                    return FileVisitResult.CONTINUE;
                }
            }
        );
        return new Result(bytes.get(), skippedEntries.get());
    }

    private static boolean inside(Path root, Path candidate) {
        return candidate.toAbsolutePath().normalize().startsWith(root);
    }

    public record Result(long bytes, int skippedEntries) {

        public Result {
            if (bytes < 0L || skippedEntries < 0) {
                throw new IllegalArgumentException("World size result must be non-negative");
            }
        }
    }
}
