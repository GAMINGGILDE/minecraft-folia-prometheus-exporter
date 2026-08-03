package de.minecraftgilde.prometheus.folia.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RegionObservationRegistryTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

    @Test
    void acceptsParallelUpdatesAndCommitsInDeterministicOrder() throws Exception {
        RegionObservationRegistry registry = new RegionObservationRegistry();
        registry.start();
        long run = registry.beginRun().orElseThrow();
        int observations = 100;
        CountDownLatch complete = new CountDownLatch(observations);
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = observations - 1; index >= 0; index--) {
                int coordinate = index;
                executor.execute(() -> {
                    registry.update(run, observation(coordinate, NOW));
                    complete.countDown();
                });
            }
            assertTrue(complete.await(5, TimeUnit.SECONDS));
        }

        List<RegionObservation> committed = registry.completeRun(run).orElseThrow();

        assertEquals(observations, committed.size());
        assertEquals(0, committed.getFirst().key().chunkX());
        assertEquals(99, committed.getLast().key().chunkX());
    }

    @Test
    void rejectsLateAndOlderResultsAndKeepsLastCommitOnFailure() {
        RegionObservationRegistry registry = new RegionObservationRegistry();
        registry.start();
        long first = registry.beginRun().orElseThrow();
        assertTrue(registry.update(first, observation(1, NOW)));
        registry.completeRun(first).orElseThrow();
        assertFalse(registry.update(first, observation(2, NOW)));

        long second = registry.beginRun().orElseThrow();
        assertTrue(registry.update(second, observation(1, NOW)));
        assertFalse(
            registry.update(second, observation(1, NOW.minusSeconds(1)))
        );
        assertTrue(registry.failRun(second));

        assertEquals(
            List.of(1),
            registry.current(NOW, Duration.ofMinutes(1))
                .stream()
                .map(value -> value.key().chunkX())
                .toList()
        );
    }

    @Test
    void expiresStaleValuesReplacesFormerRegionsAndRejectsAfterStop() {
        RegionObservationRegistry registry = new RegionObservationRegistry();
        registry.start();
        long first = registry.beginRun().orElseThrow();
        registry.update(first, observation(1, NOW.minusSeconds(61)));
        registry.completeRun(first).orElseThrow();
        assertTrue(registry.current(NOW, Duration.ofSeconds(60)).isEmpty());

        long second = registry.beginRun().orElseThrow();
        registry.update(second, observation(2, NOW));
        registry.completeRun(second).orElseThrow();
        assertEquals(1, registry.current(NOW, Duration.ofSeconds(60)).size());

        registry.stop();
        assertTrue(registry.beginRun().isEmpty());
        assertFalse(registry.update(second, observation(3, NOW)));
    }

    private static RegionObservation observation(int chunkX, Instant time) {
        return new RegionObservation(
            new RegionObservationKey("world", chunkX, 0),
            time,
            Map.of(TpsWindow.FIVE_SECONDS, 20.0),
            0
        );
    }
}
