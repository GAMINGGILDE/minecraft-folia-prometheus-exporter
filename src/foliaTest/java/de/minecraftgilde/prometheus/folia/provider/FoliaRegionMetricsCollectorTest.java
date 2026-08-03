package de.minecraftgilde.prometheus.folia.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.snapshot.ImmutableSnapshot;
import io.prometheus.metrics.config.EscapingScheme;
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FoliaRegionMetricsCollectorTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final List<String> STATISTICS = List.of(
        "min",
        "p05",
        "p50",
        "p95",
        "max",
        "average"
    );

    @Test
    void exposesAllSupportedFamiliesWithBoundedDeterministicLabels()
        throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        FoliaMetrics metrics = metrics(registry);
        metrics.register();
        metrics.repository().publish(
            new ImmutableSnapshot<>(
                NOW,
                List.of(
                    observation(0, NOW.minusSeconds(20), 20.0, 0),
                    observation(1, NOW.minusSeconds(10), 10.0, 3)
                )
            )
        );

        String output = exposition(registry);

        for (String family : List.of(
            "minecraft_folia_observed_regions",
            "minecraft_folia_region_tps",
            "minecraft_folia_regions_below_tps",
            "minecraft_folia_regions_with_players",
            "minecraft_folia_players_per_region",
            "minecraft_folia_region_snapshot_age_seconds"
        )) {
            assertTrue(output.contains("# HELP " + family + " "));
            assertTrue(output.contains("# TYPE " + family + " gauge"));
        }
        assertTrue(output.contains(
            "minecraft_folia_observed_regions{world=\"world\"} 2.0"
        ));
        assertTrue(output.contains(
            "minecraft_folia_region_tps{stat=\"min\",window=\"5s\",world=\"world\"} 10.0"
        ));
        assertTrue(output.contains(
            "minecraft_folia_region_tps{stat=\"p50\",window=\"5s\",world=\"world\"} 15.0"
        ));
        assertTrue(output.contains(
            "minecraft_folia_regions_below_tps{threshold=\"19\",window=\"5s\",world=\"world\"} 1.0"
        ));
        assertTrue(output.contains(
            "minecraft_folia_regions_with_players{world=\"world\"} 1.0"
        ));
        assertTrue(output.contains(
            "minecraft_folia_region_snapshot_age_seconds{world=\"world\"} 20.0"
        ));
        assertFalse(output.contains("chunk"));
        assertFalse(output.contains("region_id"));
        assertFalse(output.matches("(?s).*[0-9a-f]{8}-[0-9a-f-]{27,}.*"));
    }

    @Test
    void expiredOrEmptySnapshotsProduceNoInventedZeroSamples() throws Exception {
        PrometheusRegistry registry = new PrometheusRegistry();
        FoliaMetrics metrics = metrics(registry);
        metrics.register();
        metrics.repository().publish(
            new ImmutableSnapshot<>(
                NOW.minusSeconds(61),
                List.of(observation(0, NOW.minusSeconds(61), 20.0, 1))
            )
        );

        String output = exposition(registry);

        assertTrue(output.lines().noneMatch(line -> line.startsWith(
            "minecraft_folia_region_tps{"
        )));
        assertTrue(output.lines().noneMatch(line -> line.startsWith(
            "minecraft_folia_observed_regions{"
        )));
    }

    private static FoliaMetrics metrics(PrometheusRegistry registry) {
        return new FoliaMetrics(
            registry,
            CLOCK,
            Duration.ofSeconds(60),
            List.of(TpsWindow.FIVE_SECONDS),
            STATISTICS,
            TpsThreshold.configured(List.of(19.0, 18.0, 15.0))
        );
    }

    private static RegionObservation observation(
        int chunkX,
        Instant observedAt,
        double tps,
        int players
    ) {
        return new RegionObservation(
            new RegionObservationKey("world", chunkX, 0),
            observedAt,
            Map.of(TpsWindow.FIVE_SECONDS, tps),
            players
        );
    }

    private static String exposition(PrometheusRegistry registry)
        throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrometheusTextFormatWriter.create().write(
            output,
            registry.scrape(),
            EscapingScheme.DEFAULT
        );
        return output.toString(StandardCharsets.UTF_8);
    }
}
