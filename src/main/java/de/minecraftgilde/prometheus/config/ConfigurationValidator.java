package de.minecraftgilde.prometheus.config;

import de.minecraftgilde.prometheus.config.ExporterConfiguration.CollectionConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.HttpConfiguration;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates configuration semantics independently of a Minecraft server. */
public final class ConfigurationValidator {

    private static final Set<String> ALLOWED_TPS_STATISTICS = Set.of(
        "min",
        "p05",
        "p50",
        "p95",
        "max",
        "average"
    );

    public void validate(ExporterConfiguration configuration) {
        validateHttp(configuration.http());
        validateCollection(configuration.collection());
        validateFolia(configuration.folia());

        if (configuration.privacy().individualPlayerMetricsSupported()) {
            throw new ConfigurationException(
                "Individual player metrics are not supported and cannot be enabled"
            );
        }
    }

    private static void validateHttp(HttpConfiguration http) {
        requireNotBlank("http.bind-address", http.bindAddress());
        if (http.port() < 1 || http.port() > 65_535) {
            throw new ConfigurationException("http.port must be between 1 and 65535");
        }
        if (http.workerThreads() < 1) {
            throw new ConfigurationException("http.worker-threads must be positive");
        }

        List<String> paths = List.of(
            http.metricsPath(),
            http.healthPath(),
            http.readyPath()
        );
        for (String path : paths) {
            if (!path.startsWith("/") || path.length() < 2) {
                throw new ConfigurationException(
                    "HTTP paths must start with '/' and contain a path segment"
                );
            }
        }
        if (new HashSet<>(paths).size() != paths.size()) {
            throw new ConfigurationException("HTTP endpoint paths must be unique");
        }
    }

    private static void validateCollection(CollectionConfiguration collection) {
        requireTickInterval(
            "collection.server-interval",
            collection.serverInterval()
        );
        requireTickInterval(
            "collection.world-interval",
            collection.worldInterval()
        );
        requireTickInterval(
            "collection.region-interval",
            collection.regionInterval()
        );
        requireTickInterval(
            "collection.entity-interval",
            collection.entityInterval()
        );
        requireMillisecondDuration(
            "collection.filesystem-interval",
            collection.filesystemInterval()
        );
        requireMillisecondDuration("collection.timeout", collection.timeout());
    }

    private static void validateFolia(
        ExporterConfiguration.FoliaConfiguration folia
    ) {
        requirePositive("folia.observation-ttl", folia.observationTtl());

        for (String location : folia.observationSources().configuredLocations()) {
            requireNotBlank("folia.observation-sources.configured-locations", location);
        }

        if (folia.tps().windows().isEmpty()) {
            throw new ConfigurationException("folia.tps.windows must not be empty");
        }
        for (String window : folia.tps().windows()) {
            DurationParser.parse("folia.tps.windows", window);
        }

        if (folia.tps().statistics().isEmpty()) {
            throw new ConfigurationException("folia.tps.statistics must not be empty");
        }
        for (String statistic : folia.tps().statistics()) {
            if (!ALLOWED_TPS_STATISTICS.contains(statistic)) {
                throw new ConfigurationException(
                    "Unsupported folia.tps statistic: " + statistic
                );
            }
        }

        if (folia.tps().thresholds().isEmpty()) {
            throw new ConfigurationException("folia.tps.thresholds must not be empty");
        }
        for (double threshold : folia.tps().thresholds()) {
            if (!Double.isFinite(threshold) || threshold <= 0.0 || threshold > 20.0) {
                throw new ConfigurationException(
                    "folia.tps.thresholds must be finite and between 0 and 20"
                );
            }
        }
    }

    private static void requirePositive(String path, Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            throw new ConfigurationException(path + " must be positive");
        }
    }

    private static void requireTickInterval(String path, Duration duration) {
        requireMillisecondDuration(path, duration);
        if (duration.compareTo(Duration.ofMillis(50)) < 0) {
            throw new ConfigurationException(
                path + " must be at least 50ms (one server tick)"
            );
        }
    }

    private static void requireMillisecondDuration(
        String path,
        Duration duration
    ) {
        requirePositive(path, duration);
        final long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException overflow) {
            throw new ConfigurationException(
                path + " is outside the supported millisecond range",
                overflow
            );
        }
        if (millis < 1L) {
            throw new ConfigurationException(path + " must be at least 1ms");
        }
    }

    private static void requireNotBlank(String path, String value) {
        if (value.isBlank()) {
            throw new ConfigurationException(path + " must not be blank");
        }
    }
}
