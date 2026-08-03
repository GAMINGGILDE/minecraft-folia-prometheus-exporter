package de.minecraftgilde.prometheus.folia.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FoliaStatisticsTest {

    private static final List<String> ALL = List.of(
        "min",
        "p05",
        "p50",
        "p95",
        "max",
        "average"
    );

    @Test
    void emptyInputProducesNoInventedStatistics() {
        assertTrue(FoliaStatistics.summarize(List.of(), ALL).isEmpty());
    }

    @Test
    void oneValueProducesTheSameExactStatisticWithoutRounding() {
        Map<String, Double> result = FoliaStatistics.summarize(List.of(17.25), ALL);
        for (double value : result.values()) {
            assertEquals(17.25, value);
        }
    }

    @Test
    void evenAndOddInputsUseTypeSevenLinearInterpolation() {
        Map<String, Double> even = FoliaStatistics.summarize(
            List.of(4.0, 1.0, 3.0, 2.0),
            ALL
        );
        assertEquals(1.0, even.get("min"));
        assertEquals(1.15, even.get("p05"), 1.0e-12);
        assertEquals(2.5, even.get("p50"));
        assertEquals(3.85, even.get("p95"), 1.0e-12);
        assertEquals(4.0, even.get("max"));
        assertEquals(2.5, even.get("average"));

        Map<String, Double> odd = FoliaStatistics.summarize(
            List.of(3.0, 1.0, 2.0),
            ALL
        );
        assertEquals(1.1, odd.get("p05"), 1.0e-12);
        assertEquals(2.0, odd.get("p50"));
        assertEquals(2.9, odd.get("p95"), 1.0e-12);
    }

    @Test
    void excludesNonFiniteAndNegativeInputsDeterministically() {
        Map<String, Double> result = FoliaStatistics.summarize(
            List.of(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 5.0),
            ALL
        );
        assertEquals(1, result.values().stream().distinct().count());
        assertEquals(5.0, result.get("average"));
    }

    @Test
    void thresholdLabelsAreCanonicalAndSortedDescending() {
        List<TpsThreshold> thresholds = TpsThreshold.configured(
            List.of(15.0, 19.0, 18.5)
        );
        assertEquals(List.of("19", "18.5", "15"), thresholds
            .stream()
            .map(TpsThreshold::label)
            .toList());
        assertThrows(IllegalArgumentException.class, () -> new TpsThreshold(-1));
    }
}
