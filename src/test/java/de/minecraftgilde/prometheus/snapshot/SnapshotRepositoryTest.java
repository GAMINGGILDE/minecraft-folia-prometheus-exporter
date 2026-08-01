package de.minecraftgilde.prometheus.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class SnapshotRepositoryTest {

    @Test
    void snapshotDefensivelyCopiesItsValues() {
        List<Integer> source = new ArrayList<>(List.of(1, 2));
        ImmutableSnapshot<Integer> snapshot = new ImmutableSnapshot<>(
            Instant.parse("2026-08-01T10:00:00Z"),
            source
        );

        source.add(3);

        assertEquals(List.of(1, 2), snapshot.values());
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.values().add(4)
        );
    }

    @Test
    void publishesReplacesAgesAndClearsSnapshots() {
        SnapshotRepository<Integer> repository = new SnapshotRepository<>();
        Instant capturedAt = Instant.parse("2026-08-01T10:00:00Z");
        ImmutableSnapshot<Integer> first = new ImmutableSnapshot<>(
            capturedAt,
            List.of(1)
        );
        ImmutableSnapshot<Integer> second = new ImmutableSnapshot<>(
            capturedAt.plusSeconds(5),
            List.of(2)
        );

        assertFalse(repository.hasSnapshot());
        assertTrue(repository.publish(first).isEmpty());
        assertSame(first, repository.current().orElseThrow());
        assertEquals(capturedAt, repository.capturedAt().orElseThrow());
        assertEquals(
            Duration.ofSeconds(10),
            repository
                .age(Clock.fixed(capturedAt.plusSeconds(10), ZoneOffset.UTC))
                .orElseThrow()
        );
        assertSame(first, repository.publish(second).orElseThrow());
        assertSame(second, repository.clear().orElseThrow());
        assertFalse(repository.hasSnapshot());
    }

    @Test
    void concurrentReadersNeverObservePartialSnapshots() throws Exception {
        SnapshotRepository<Integer> repository = new SnapshotRepository<>();
        repository.publish(new ImmutableSnapshot<>(Instant.EPOCH, List.of(0, 0)));

        try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
            Future<?> writer = executor.submit(() -> {
                for (int value = 1; value <= 5_000; value++) {
                    repository.publish(
                        new ImmutableSnapshot<>(
                            Instant.ofEpochSecond(value),
                            List.of(value, value)
                        )
                    );
                }
            });
            List<Future<?>> readers = new ArrayList<>();
            for (int reader = 0; reader < 4; reader++) {
                readers.add(executor.submit(() -> {
                    for (int iteration = 0; iteration < 5_000; iteration++) {
                        List<Integer> values = repository
                            .current()
                            .orElseThrow()
                            .values();
                        assertEquals(values.get(0), values.get(1));
                    }
                }));
            }

            writer.get();
            for (Future<?> reader : readers) {
                reader.get();
            }
        }
    }
}
