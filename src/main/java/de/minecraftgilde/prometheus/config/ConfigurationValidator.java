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
    private static final Set<String> ALLOWED_TPS_WINDOWS = Set.of(
        "5s",
        "15s",
        "1m",
        "5m",
        "15m"
    );

    public void validate(ExporterConfiguration configuration) {
        validateHttp(configuration.http());
        validateCollection(configuration.collection());
        validateFilesystem(configuration.filesystem());
        validateFolia(configuration.folia());
        validateEntities(configuration.entities());
        if (
            configuration.folia().observationTtl().compareTo(
                configuration.collection().foliaInterval()
            ) < 0
        ) {
            throw new ConfigurationException(
                "folia.observation-ttl must be at least collection.folia-interval"
            );
        }

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
            "collection.folia-interval",
            collection.foliaInterval()
        );
        requireMillisecondDuration(
            "collection.filesystem-interval",
            collection.filesystemInterval()
        );
        requireMillisecondDuration("collection.timeout", collection.timeout());
        requireMillisecondDuration(
            "collection.filesystem-timeout",
            collection.filesystemTimeout()
        );
    }

    private static void validateEntities(
        ExporterConfiguration.EntitiesConfiguration entities
    ) {
        requireMillisecondDuration(
            "entities.reconciliation-interval",
            entities.reconciliationInterval()
        );
        if (
            entities.reconciliationInterval().compareTo(Duration.ofMinutes(1)) < 0
        ) {
            throw new ConfigurationException(
                "entities.reconciliation-interval must be at least 1m"
            );
        }
        requireMillisecondDuration(
            "entities.reconciliation-timeout",
            entities.reconciliationTimeout()
        );
    }

    private static void validateFilesystem(
        ExporterConfiguration.FilesystemConfiguration filesystem
    ) {
        int concurrency = filesystem.worldSizeScanConcurrency();
        if (concurrency < 1 || concurrency > 8) {
            throw new ConfigurationException(
                "filesystem.world-size-scan-concurrency must be between 1 and 8"
            );
        }
    }

    private static void validateFolia(
        ExporterConfiguration.FoliaConfiguration folia
    ) {
        requireMillisecondDuration(
            "folia.observation-ttl",
            folia.observationTtl()
        );

        if (
            !folia.observationSources().playerRegions()
                && !folia.observationSources().worldSpawns()
                && !folia.observationSources().forceLoadedChunks()
        ) {
            throw new ConfigurationException(
                "At least one public folia.observation-sources source must be enabled"
            );
        }

        if (!folia.observationSources().configuredLocations().isEmpty()) {
            throw new ConfigurationException(
                "folia.observation-sources.configured-locations is not supported in Phase 6"
            );
        }

        if (folia.tps().windows().isEmpty()) {
            throw new ConfigurationException("folia.tps-windows must not be empty");
        }
        Set<String> windows = new HashSet<>();
        for (String window : folia.tps().windows()) {
            if (!ALLOWED_TPS_WINDOWS.contains(window)) {
                throw new ConfigurationException(
                    "Unsupported folia.tps-windows value: " + window
                );
            }
            if (!windows.add(window)) {
                throw new ConfigurationException(
                    "Duplicate folia.tps-windows value: " + window
                );
            }
        }

        if (folia.tps().statistics().isEmpty()) {
            throw new ConfigurationException("folia.tps-statistics must not be empty");
        }
        Set<String> statistics = new HashSet<>();
        for (String statistic : folia.tps().statistics()) {
            if (!ALLOWED_TPS_STATISTICS.contains(statistic)) {
                throw new ConfigurationException(
                    "Unsupported folia.tps-statistics value: " + statistic
                );
            }
            if (!statistics.add(statistic)) {
                throw new ConfigurationException(
                    "Duplicate folia.tps-statistics value: " + statistic
                );
            }
        }

        if (folia.tps().thresholds().isEmpty()) {
            throw new ConfigurationException("folia.tps-thresholds must not be empty");
        }
        Set<Double> thresholds = new HashSet<>();
        for (double threshold : folia.tps().thresholds()) {
            if (!Double.isFinite(threshold) || threshold <= 0.0 || threshold > 20.0) {
                throw new ConfigurationException(
                    "folia.tps-thresholds must be finite and between 0 and 20"
                );
            }
            if (!thresholds.add(threshold)) {
                throw new ConfigurationException(
                    "folia.tps-thresholds must not contain duplicates"
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
