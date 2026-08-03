package de.minecraftgilde.prometheus.config;

import de.minecraftgilde.prometheus.config.ExporterConfiguration.CollectionConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.CollectorsConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.FilesystemConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.FoliaConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.HttpConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.LoggingConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.ObservationSourcesConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.PrivacyConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.TpsConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Loads the immutable model from a server-independent value source. */
public final class ConfigurationLoader {

    public ExporterConfiguration load(ConfigurationSource source) {
        Objects.requireNonNull(source, "source");
        ExporterConfiguration defaults = ExporterConfiguration.defaults();

        return new ExporterConfiguration(
            loadHttp(source, defaults.http()),
            loadCollection(source, defaults.collection()),
            loadCollectors(source, defaults.collectors()),
            loadFolia(source, defaults.folia()),
            loadFilesystem(source, defaults.filesystem()),
            new PrivacyConfiguration(
                booleanValue(
                    source,
                    "privacy.individual-player-metrics-supported",
                    defaults.privacy().individualPlayerMetricsSupported()
                )
            ),
            new LoggingConfiguration(
                booleanValue(
                    source,
                    "logging.collection-errors",
                    defaults.logging().collectionErrors()
                ),
                booleanValue(source, "logging.debug", defaults.logging().debug())
            )
        );
    }

    private static HttpConfiguration loadHttp(
        ConfigurationSource source,
        HttpConfiguration defaults
    ) {
        return new HttpConfiguration(
            stringValue(source, "http.bind-address", defaults.bindAddress()),
            intValue(source, "http.port", defaults.port()),
            stringValue(source, "http.metrics-path", defaults.metricsPath()),
            stringValue(source, "http.health-path", defaults.healthPath()),
            stringValue(source, "http.ready-path", defaults.readyPath()),
            intValue(source, "http.worker-threads", defaults.workerThreads())
        );
    }

    private static CollectionConfiguration loadCollection(
        ConfigurationSource source,
        CollectionConfiguration defaults
    ) {
        return new CollectionConfiguration(
            durationValue(source, "collection.server-interval", defaults.serverInterval()),
            durationValue(source, "collection.world-interval", defaults.worldInterval()),
            durationValue(
                source,
                "collection.folia-interval",
                "collection.region-interval",
                defaults.foliaInterval()
            ),
            durationValue(source, "collection.entity-interval", defaults.entityInterval()),
            durationValue(
                source,
                "collection.filesystem-interval",
                defaults.filesystemInterval()
            ),
            durationValue(source, "collection.timeout", defaults.timeout()),
            durationValue(
                source,
                "collection.filesystem-timeout",
                defaults.filesystemTimeout()
            )
        );
    }

    private static CollectorsConfiguration loadCollectors(
        ConfigurationSource source,
        CollectorsConfiguration defaults
    ) {
        return new CollectorsConfiguration(
            booleanValue(source, "collectors.server", defaults.server()),
            booleanValue(source, "collectors.events", defaults.events()),
            booleanValue(source, "collectors.worlds", defaults.worlds()),
            booleanValue(source, "collectors.chunks", defaults.chunks()),
            booleanValue(source, "collectors.entities", defaults.entities()),
            booleanValue(
                source,
                "collectors.folia",
                "collectors.folia-regions",
                defaults.folia()
            ),
            booleanValue(source, "collectors.jvm", defaults.jvm()),
            booleanValue(source, "collectors.process", defaults.process()),
            booleanValue(source, "collectors.filesystem", defaults.filesystem()),
            booleanValue(source, "collectors.exporter", defaults.exporter()),
            booleanValue(source, "collectors.gameplay", defaults.gameplay()),
            booleanValue(source, "collectors.plugin-info", defaults.pluginInfo()),
            booleanValue(
                source,
                "collectors.detailed-entity-types",
                defaults.detailedEntityTypes()
            ),
            booleanValue(source, "collectors.commands", defaults.commands())
        );
    }

    private static FoliaConfiguration loadFolia(
        ConfigurationSource source,
        FoliaConfiguration defaults
    ) {
        ObservationSourcesConfiguration observationDefaults = defaults.observationSources();
        TpsConfiguration tpsDefaults = defaults.tps();

        return new FoliaConfiguration(
            new ObservationSourcesConfiguration(
                booleanValue(
                    source,
                    "folia.observation-sources.player-regions",
                    observationDefaults.playerRegions()
                ),
                booleanValue(
                    source,
                    "folia.observation-sources.world-spawns",
                    observationDefaults.worldSpawns()
                ),
                booleanValue(
                    source,
                    "folia.observation-sources.force-loaded-chunks",
                    observationDefaults.forceLoadedChunks()
                ),
                stringList(
                    source,
                    "folia.observation-sources.configured-locations",
                    observationDefaults.configuredLocations()
                )
            ),
            durationValue(source, "folia.observation-ttl", defaults.observationTtl()),
            new TpsConfiguration(
                stringList(
                    source,
                    "folia.tps-windows",
                    "folia.tps.windows",
                    tpsDefaults.windows()
                ),
                stringList(
                    source,
                    "folia.tps-statistics",
                    "folia.tps.statistics",
                    tpsDefaults.statistics()
                ),
                doubleList(
                    source,
                    "folia.tps-thresholds",
                    "folia.tps.thresholds",
                    tpsDefaults.thresholds()
                )
            )
        );
    }

    private static FilesystemConfiguration loadFilesystem(
        ConfigurationSource source,
        FilesystemConfiguration defaults
    ) {
        return new FilesystemConfiguration(
            booleanValue(
                source,
                "filesystem.include-world-sizes",
                defaults.includeWorldSizes()
            ),
            intValue(
                source,
                "filesystem.world-size-scan-concurrency",
                defaults.worldSizeScanConcurrency()
            ),
            booleanValue(
                source,
                "filesystem.include-server-filesystem",
                defaults.includeServerFilesystem()
            ),
            booleanValue(
                source,
                "filesystem.include-log-size",
                defaults.includeLogSize()
            ),
            booleanValue(
                source,
                "filesystem.include-plugin-size",
                defaults.includePluginSize()
            )
        );
    }

    private static String stringValue(
        ConfigurationSource source,
        String path,
        String defaultValue
    ) {
        Object value = source.get(path);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String string) {
            return string;
        }
        throw typeError(path, "string", value);
    }

    private static int intValue(
        ConfigurationSource source,
        String path,
        int defaultValue
    ) {
        Object value = source.get(path);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            double numericValue = number.doubleValue();
            if (
                Double.isFinite(numericValue)
                    && numericValue == Math.rint(numericValue)
                    && numericValue >= Integer.MIN_VALUE
                    && numericValue <= Integer.MAX_VALUE
            ) {
                return number.intValue();
            }
        }
        throw typeError(path, "integer", value);
    }

    private static boolean booleanValue(
        ConfigurationSource source,
        String path,
        boolean defaultValue
    ) {
        Object value = source.get(path);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw typeError(path, "boolean", value);
    }

    private static boolean booleanValue(
        ConfigurationSource source,
        String path,
        String legacyPath,
        boolean defaultValue
    ) {
        if (source.get(path) != null) {
            return booleanValue(source, path, defaultValue);
        }
        return booleanValue(source, legacyPath, defaultValue);
    }

    private static Duration durationValue(
        ConfigurationSource source,
        String path,
        Duration defaultValue
    ) {
        Object value = source.get(path);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String string) {
            return DurationParser.parse(path, string);
        }
        throw typeError(path, "duration string", value);
    }

    private static Duration durationValue(
        ConfigurationSource source,
        String path,
        String legacyPath,
        Duration defaultValue
    ) {
        if (source.get(path) != null) {
            return durationValue(source, path, defaultValue);
        }
        return durationValue(source, legacyPath, defaultValue);
    }

    private static List<String> stringList(
        ConfigurationSource source,
        String path,
        List<String> defaultValue
    ) {
        Object value = source.get(path);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof List<?> list)) {
            throw typeError(path, "list of strings", value);
        }

        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof String string)) {
                throw typeError(path, "list of strings", value);
            }
            result.add(string);
        }
        return List.copyOf(result);
    }

    private static List<String> stringList(
        ConfigurationSource source,
        String path,
        String legacyPath,
        List<String> defaultValue
    ) {
        if (source.get(path) != null) {
            return stringList(source, path, defaultValue);
        }
        return stringList(source, legacyPath, defaultValue);
    }

    private static List<Double> doubleList(
        ConfigurationSource source,
        String path,
        List<Double> defaultValue
    ) {
        Object value = source.get(path);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof List<?> list)) {
            throw typeError(path, "list of numbers", value);
        }

        List<Double> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Number number)) {
                throw typeError(path, "list of numbers", value);
            }
            result.add(number.doubleValue());
        }
        return List.copyOf(result);
    }

    private static List<Double> doubleList(
        ConfigurationSource source,
        String path,
        String legacyPath,
        List<Double> defaultValue
    ) {
        if (source.get(path) != null) {
            return doubleList(source, path, defaultValue);
        }
        return doubleList(source, legacyPath, defaultValue);
    }

    private static ConfigurationException typeError(
        String path,
        String expectedType,
        Object value
    ) {
        return new ConfigurationException(
            "Configuration value '" + path + "' must be a " + expectedType
                + ", but was " + value.getClass().getSimpleName()
        );
    }
}
