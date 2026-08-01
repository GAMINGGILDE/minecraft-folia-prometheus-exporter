package de.minecraftgilde.prometheus.config;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DurationParser {

    private static final Pattern DURATION_PATTERN = Pattern.compile(
        "^([1-9][0-9]*)(ms|s|m|h)$"
    );

    private DurationParser() {}

    static Duration parse(String path, String value) {
        Matcher matcher = DURATION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new ConfigurationException(
                "Configuration value '" + path + "' must be a positive duration "
                    + "using ms, s, m or h: " + value
            );
        }

        try {
            long amount = Long.parseLong(matcher.group(1));
            return switch (matcher.group(2)) {
                case "ms" -> Duration.ofMillis(amount);
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                default -> throw new IllegalStateException("Unexpected duration unit");
            };
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new ConfigurationException(
                "Configuration value '" + path + "' is outside the supported range",
                exception
            );
        }
    }
}
