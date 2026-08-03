package de.minecraftgilde.prometheus.folia.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Fixed order documented by Folia's public region-TPS API. */
enum TpsWindow {
    FIVE_SECONDS("5s", 0),
    FIFTEEN_SECONDS("15s", 1),
    ONE_MINUTE("1m", 2),
    FIVE_MINUTES("5m", 3),
    FIFTEEN_MINUTES("15m", 4);

    static final int API_VALUE_COUNT = 5;
    static final double MAX_PUBLIC_TICK_RATE = 10_000.0;

    private final String label;
    private final int apiIndex;

    TpsWindow(String label, int apiIndex) {
        this.label = label;
        this.apiIndex = apiIndex;
    }

    String label() {
        return label;
    }

    static List<TpsWindow> configured(Collection<String> labels) {
        List<TpsWindow> result = new ArrayList<>();
        for (TpsWindow window : values()) {
            if (labels.contains(window.label)) {
                result.add(window);
            }
        }
        return List.copyOf(result);
    }

    static Map<TpsWindow, Double> read(
        double[] apiValues,
        List<TpsWindow> configured
    ) {
        if (apiValues.length < API_VALUE_COUNT) {
            throw new IllegalArgumentException(
                "Public Folia region TPS result must contain five windows"
            );
        }
        EnumMap<TpsWindow, Double> values = new EnumMap<>(TpsWindow.class);
        for (TpsWindow window : configured) {
            double value = apiValues[window.apiIndex];
            if (
                !Double.isFinite(value)
                    || value < 0.0
                    || value > MAX_PUBLIC_TICK_RATE
            ) {
                throw new IllegalArgumentException(
                    "Public Folia region TPS value is outside the supported range"
                );
            }
            values.put(window, value);
        }
        return Map.copyOf(values);
    }
}
