package de.minecraftgilde.prometheus.folia.provider;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic exact statistics using type-7 linear quantile interpolation. */
final class FoliaStatistics {

    private FoliaStatistics() {}

    static Map<String, Double> summarize(
        Collection<Double> input,
        List<String> configuredStatistics
    ) {
        double[] values = input
            .stream()
            .filter(value -> value != null && Double.isFinite(value) && value >= 0.0)
            .mapToDouble(Double::doubleValue)
            .sorted()
            .toArray();
        if (values.length == 0) {
            return Map.of();
        }

        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (String statistic : configuredStatistics) {
            double value = switch (statistic) {
                case "min" -> values[0];
                case "p05" -> quantile(values, 0.05);
                case "p50" -> quantile(values, 0.50);
                case "p95" -> quantile(values, 0.95);
                case "max" -> values[values.length - 1];
                case "average" -> sum / values.length;
                default -> throw new IllegalArgumentException(
                    "Unsupported statistic: " + statistic
                );
            };
            result.put(statistic, value);
        }
        return Collections.unmodifiableMap(result);
    }

    static double quantile(double[] sortedValues, double probability) {
        if (sortedValues.length == 0) {
            throw new IllegalArgumentException("Quantile input must not be empty");
        }
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("Probability must be between 0 and 1");
        }
        double index = (sortedValues.length - 1) * probability;
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sortedValues[lower];
        }
        double fraction = index - lower;
        return sortedValues[lower]
            + fraction * (sortedValues[upper] - sortedValues[lower]);
    }
}
