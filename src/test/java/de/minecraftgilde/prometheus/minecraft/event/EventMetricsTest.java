package de.minecraftgilde.prometheus.minecraft.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.config.EscapingScheme;
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class EventMetricsTest {

    private static final List<String> FAMILIES = List.of(
        "minecraft_login_attempts_total",
        "minecraft_login_denied_total",
        "minecraft_player_joins_total",
        "minecraft_player_quits_total",
        "minecraft_player_kicks_total",
        "minecraft_server_list_pings_total",
        "minecraft_chat_messages_total",
        "minecraft_chunks_loaded_total",
        "minecraft_chunks_unloaded_total",
        "minecraft_chunks_generated_total"
    );

    @Test
    void registersExactlyThePhaseFiveFamiliesInEachPrivateRegistry() {
        PrometheusRegistry firstRegistry = new PrometheusRegistry();
        PrometheusRegistry secondRegistry = new PrometheusRegistry();

        EventMetrics firstMetrics = new EventMetrics(firstRegistry);
        EventMetrics secondMetrics = new EventMetrics(secondRegistry);
        firstMetrics.initializeWorld("world");
        secondMetrics.initializeWorld("world");

        assertNotSame(firstRegistry, secondRegistry);
        List<String> expected = FAMILIES.stream().sorted().toList();
        assertEquals(expected, familyNames(firstRegistry));
        assertEquals(expected, familyNames(secondRegistry));
        assertThrows(RuntimeException.class, () -> new EventMetrics(firstRegistry));
        assertEquals(expected, familyNames(firstRegistry));

        String exposition = exposition(firstRegistry);
        for (String family : FAMILIES) {
            assertTrue(exposition.contains("# HELP " + family + " "), family);
            assertTrue(exposition.contains("# TYPE " + family + " counter"), family);
        }
    }

    @Test
    void concurrentEventsAcrossWorldsLoseNoIncrements() throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        EventMetrics metrics = new EventMetrics(registry);
        int threads = 8;
        int iterations = 1_000;

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                int threadIndex = thread;
                futures.add(executor.submit(() -> {
                    String world = threadIndex % 2 == 0 ? "world" : "world_nether";
                    for (int iteration = 0; iteration < iterations; iteration++) {
                        metrics.recordLogin(
                            iteration % 2 == 0
                                ? PlayerLoginEvent.Result.KICK_BANNED
                                : PlayerLoginEvent.Result.ALLOWED
                        );
                        metrics.recordJoin();
                        metrics.recordQuit();
                        metrics.recordKick(PlayerKickEvent.Cause.PLUGIN);
                        metrics.recordServerListPing();
                        metrics.recordChatMessage();
                        metrics.recordChunkLoad(world, iteration % 2 == 0);
                        metrics.recordChunkUnload(world);
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }

        double total = threads * iterations;
        assertEquals(total, value(registry, "minecraft_login_attempts_total"));
        assertEquals(total / 2, value(registry, "minecraft_login_denied_total", "reason", "banned"));
        assertEquals(total, value(registry, "minecraft_player_joins_total"));
        assertEquals(total, value(registry, "minecraft_player_quits_total"));
        assertEquals(total, value(registry, "minecraft_player_kicks_total", "reason", "plugin"));
        assertEquals(total, value(registry, "minecraft_server_list_pings_total"));
        assertEquals(total, value(registry, "minecraft_chat_messages_total"));
        assertEquals(total / 2, value(registry, "minecraft_chunks_loaded_total", "world", "world"));
        assertEquals(total / 2, value(registry, "minecraft_chunks_loaded_total", "world", "world_nether"));
        assertEquals(total / 4, value(registry, "minecraft_chunks_generated_total", "world", "world"));
        assertEquals(total / 4, value(registry, "minecraft_chunks_generated_total", "world", "world_nether"));
        assertEquals(total / 2, value(registry, "minecraft_chunks_unloaded_total", "world", "world"));
        assertEquals(total / 2, value(registry, "minecraft_chunks_unloaded_total", "world", "world_nether"));
    }

    @Test
    void rejectsBlankWorldLabelsUsingTheSharedNormalizer() {
        EventMetrics metrics = new EventMetrics(new PrometheusRegistry());

        assertThrows(IllegalArgumentException.class, () -> metrics.recordChunkLoad(" ", false));
        assertThrows(IllegalArgumentException.class, () -> metrics.recordChunkUnload(""));
    }

    private static List<String> familyNames(PrometheusRegistry registry) {
        return registry.scrape().stream()
            .map(snapshot -> snapshot.getMetadata().getExpositionBasePrometheusName())
            .sorted()
            .toList();
    }

    private static double value(PrometheusRegistry registry, String family) {
        return value(registry, family, null, null);
    }

    private static double value(
        PrometheusRegistry registry,
        String family,
        String labelName,
        String labelValue
    ) {
        CounterSnapshot snapshot = registry.scrape().stream()
            .filter(metric -> metric.getMetadata()
                .getExpositionBasePrometheusName()
                .equals(family))
            .map(CounterSnapshot.class::cast)
            .findFirst()
            .orElseThrow();
        return snapshot.getDataPoints().stream()
            .filter(point -> labelName == null
                ? point.getLabels().isEmpty()
                : labelValue.equals(point.getLabels().get(labelName)))
            .findFirst()
            .orElseThrow()
            .getValue();
    }

    private static String exposition(PrometheusRegistry registry) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrometheusTextFormatWriter.create().write(
                output,
                registry.scrape(),
                EscapingScheme.DEFAULT
            );
            return output.toString(StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
