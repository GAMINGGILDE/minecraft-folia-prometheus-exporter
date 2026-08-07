package de.minecraftgilde.prometheus.config;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Immutable, server-independent representation of {@code config.yml}. */
public record ExporterConfiguration(
    HttpConfiguration http,
    CollectionConfiguration collection,
    CollectorsConfiguration collectors,
    FoliaConfiguration folia,
    EntitiesConfiguration entities,
    FilesystemConfiguration filesystem,
    PrivacyConfiguration privacy,
    LoggingConfiguration logging
) {

    public ExporterConfiguration {
        Objects.requireNonNull(http, "http");
        Objects.requireNonNull(collection, "collection");
        Objects.requireNonNull(collectors, "collectors");
        Objects.requireNonNull(folia, "folia");
        Objects.requireNonNull(entities, "entities");
        Objects.requireNonNull(filesystem, "filesystem");
        Objects.requireNonNull(privacy, "privacy");
        Objects.requireNonNull(logging, "logging");
    }

    public static ExporterConfiguration defaults() {
        return new ExporterConfiguration(
            new HttpConfiguration(
                "127.0.0.1",
                9940,
                "/metrics",
                "/health",
                "/ready",
                2
            ),
            new CollectionConfiguration(
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(5),
                Duration.ofMinutes(30),
                Duration.ofSeconds(10),
                Duration.ofMinutes(15)
            ),
            new CollectorsConfiguration(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false
            ),
            new FoliaConfiguration(
                new ObservationSourcesConfiguration(true, true, false, List.of()),
                Duration.ofSeconds(60),
                new TpsConfiguration(
                    List.of("5s", "15s", "1m", "5m", "15m"),
                    List.of("min", "p05", "p50", "p95", "max", "average"),
                    List.of(19.0, 18.0, 15.0)
                )
            ),
            new EntitiesConfiguration(
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                false,
                false
            ),
            new FilesystemConfiguration(true, 1, true, false, false),
            new PrivacyConfiguration(false),
            new LoggingConfiguration(true, false)
        );
    }

    public record HttpConfiguration(
        String bindAddress,
        int port,
        String metricsPath,
        String healthPath,
        String readyPath,
        int workerThreads
    ) {

        public HttpConfiguration {
            Objects.requireNonNull(bindAddress, "bindAddress");
            Objects.requireNonNull(metricsPath, "metricsPath");
            Objects.requireNonNull(healthPath, "healthPath");
            Objects.requireNonNull(readyPath, "readyPath");
        }
    }

    public record CollectionConfiguration(
        Duration serverInterval,
        Duration worldInterval,
        Duration regionInterval,
        Duration filesystemInterval,
        Duration timeout,
        Duration filesystemTimeout
    ) {

        public CollectionConfiguration {
            Objects.requireNonNull(serverInterval, "serverInterval");
            Objects.requireNonNull(worldInterval, "worldInterval");
            Objects.requireNonNull(regionInterval, "regionInterval");
            Objects.requireNonNull(filesystemInterval, "filesystemInterval");
            Objects.requireNonNull(timeout, "timeout");
            Objects.requireNonNull(filesystemTimeout, "filesystemTimeout");
        }

        /** Current name for the legacy {@code regionInterval} model field. */
        public Duration foliaInterval() {
            return regionInterval;
        }
    }

    public record CollectorsConfiguration(
        boolean server,
        boolean events,
        boolean worlds,
        boolean chunks,
        boolean entities,
        boolean foliaRegions,
        boolean jvm,
        boolean process,
        boolean filesystem,
        boolean exporter,
        boolean gameplay,
        boolean pluginInfo,
        boolean commands
    ) {

        /** Current name for the legacy {@code foliaRegions} model field. */
        public boolean folia() {
            return foliaRegions;
        }
    }

    public record EntitiesConfiguration(
        Duration reconciliationInterval,
        Duration reconciliationTimeout,
        boolean includeExactTypes,
        boolean includeProjectileTotal
    ) {

        public EntitiesConfiguration {
            Objects.requireNonNull(
                reconciliationInterval,
                "reconciliationInterval"
            );
            Objects.requireNonNull(
                reconciliationTimeout,
                "reconciliationTimeout"
            );
        }
    }

    public record FoliaConfiguration(
        ObservationSourcesConfiguration observationSources,
        Duration observationTtl,
        TpsConfiguration tps
    ) {

        public FoliaConfiguration {
            Objects.requireNonNull(observationSources, "observationSources");
            Objects.requireNonNull(observationTtl, "observationTtl");
            Objects.requireNonNull(tps, "tps");
        }
    }

    public record ObservationSourcesConfiguration(
        boolean playerRegions,
        boolean worldSpawns,
        boolean forceLoadedChunks,
        List<String> configuredLocations
    ) {

        public ObservationSourcesConfiguration {
            configuredLocations = List.copyOf(
                Objects.requireNonNull(configuredLocations, "configuredLocations")
            );
        }
    }

    public record TpsConfiguration(
        List<String> windows,
        List<String> statistics,
        List<Double> thresholds
    ) {

        public TpsConfiguration {
            windows = List.copyOf(Objects.requireNonNull(windows, "windows"));
            statistics = List.copyOf(
                Objects.requireNonNull(statistics, "statistics")
            );
            thresholds = List.copyOf(
                Objects.requireNonNull(thresholds, "thresholds")
            );
        }
    }

    public record FilesystemConfiguration(
        boolean includeWorldSizes,
        int worldSizeScanConcurrency,
        boolean includeServerFilesystem,
        boolean includeLogSize,
        boolean includePluginSize
    ) {}

    public record PrivacyConfiguration(
        boolean individualPlayerMetricsSupported
    ) {}

    public record LoggingConfiguration(
        boolean collectionErrors,
        boolean debug
    ) {}
}
