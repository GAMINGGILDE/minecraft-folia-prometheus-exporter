package de.minecraftgilde.prometheus.folia.provider;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Finite threshold and locale-independent deterministic Prometheus label. */
record TpsThreshold(double value, String label) {

    TpsThreshold(double value) {
        this(value, canonicalLabel(value));
    }

    TpsThreshold {
        if (!Double.isFinite(value) || value <= 0.0 || value > 20.0) {
            throw new IllegalArgumentException("TPS threshold must be in (0, 20]");
        }
        if (!label.equals(canonicalLabel(value))) {
            throw new IllegalArgumentException("TPS threshold label is not canonical");
        }
    }

    static List<TpsThreshold> configured(List<Double> values) {
        return values
            .stream()
            .map(TpsThreshold::new)
            .sorted(Comparator.comparingDouble(TpsThreshold::value).reversed())
            .toList();
    }

    private static String canonicalLabel(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
