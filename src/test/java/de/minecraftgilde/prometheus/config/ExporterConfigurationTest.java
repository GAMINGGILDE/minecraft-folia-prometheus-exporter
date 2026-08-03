package de.minecraftgilde.prometheus.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExporterConfigurationTest {

    private final ConfigurationLoader loader = new ConfigurationLoader();
    private final ConfigurationValidator validator = new ConfigurationValidator();

    @Test
    void loadsValidImmutableDefaultsWithoutMinecraftRuntime() {
        ExporterConfiguration configuration = loader.load(path -> null);

        assertEquals(ExporterConfiguration.defaults(), configuration);
        assertDoesNotThrow(() -> validator.validate(configuration));
        assertTrue(configuration.collectors().jvm());
        assertTrue(configuration.collectors().process());
        assertTrue(configuration.collectors().server());
        assertTrue(configuration.collectors().events());
        assertTrue(configuration.collectors().worlds());
        assertTrue(configuration.collectors().chunks());
        assertTrue(configuration.collectors().folia());
        assertEquals(Duration.ofSeconds(5), configuration.collection().foliaInterval());
        assertFalse(configuration.collectors().pluginInfo());
        assertTrue(configuration.filesystem().includeWorldSizes());
        assertEquals(
            Duration.ofMinutes(15),
            configuration.collection().filesystemTimeout()
        );
        assertEquals(1, configuration.filesystem().worldSizeScanConcurrency());
        assertThrows(
            UnsupportedOperationException.class,
            () -> configuration.folia().tps().windows().add("30m")
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> configuration.folia().tps().thresholds().add(5.0)
        );
    }

    @Test
    void configuredValuesOverrideDefaults() {
        Map<String, Object> values = new HashMap<>();
        values.put("http.port", 12345);
        values.put("collection.timeout", "250ms");
        values.put("collection.filesystem-timeout", "2h");
        values.put("collectors.jvm", false);
        values.put("collectors.process", false);
        values.put("collectors.server", false);
        values.put("collectors.events", false);
        values.put("collectors.worlds", false);
        values.put("collectors.chunks", true);
        values.put("collectors.plugin-info", true);
        values.put("filesystem.include-world-sizes", false);
        values.put("filesystem.world-size-scan-concurrency", 4);
        values.put("collectors.gameplay", true);
        values.put("logging.debug", true);

        ExporterConfiguration configuration = loader.load(values::get);

        assertEquals(12345, configuration.http().port());
        assertEquals(Duration.ofMillis(250), configuration.collection().timeout());
        assertEquals(
            Duration.ofHours(2),
            configuration.collection().filesystemTimeout()
        );
        assertFalse(configuration.privacy().individualPlayerMetricsSupported());
        assertFalse(configuration.collectors().jvm());
        assertFalse(configuration.collectors().process());
        assertFalse(configuration.collectors().server());
        assertFalse(configuration.collectors().events());
        assertFalse(configuration.collectors().worlds());
        assertTrue(configuration.collectors().chunks());
        assertTrue(configuration.collectors().pluginInfo());
        assertFalse(configuration.filesystem().includeWorldSizes());
        assertEquals(4, configuration.filesystem().worldSizeScanConcurrency());
        assertTrue(configuration.collectors().gameplay());
        assertTrue(configuration.logging().debug());
        assertDoesNotThrow(() -> validator.validate(configuration));
    }

    @Test
    void rejectsInvalidValueTypesWhileLoading() {
        Map<String, Object> values = Map.of("http.port", "9940");

        assertThrows(ConfigurationException.class, () -> loader.load(values::get));
    }

    @Test
    void rejectsWrongEventCollectorTypeWithFullPath() {
        ConfigurationException failure = assertThrows(
            ConfigurationException.class,
            () -> loader.load(
                Map.<String, Object>of("collectors.events", "yes")::get
            )
        );

        assertTrue(failure.getMessage().contains("collectors.events"));
    }

    @Test
    void missingNewKeysUseDefaultsForOlderConfigurations() {
        ExporterConfiguration configuration = loader.load(
            Map.<String, Object>of(
                "collection.timeout",
                "30s",
                "filesystem.include-world-sizes",
                false
            )::get
        );

        assertEquals(Duration.ofSeconds(30), configuration.collection().timeout());
        assertEquals(
            Duration.ofMinutes(15),
            configuration.collection().filesystemTimeout()
        );
        assertEquals(1, configuration.filesystem().worldSizeScanConcurrency());
        assertDoesNotThrow(() -> validator.validate(configuration));
    }

    @Test
    void rejectsInvalidFilesystemTimeoutStringsWithFullPath() {
        for (String value : List.of("0s", "-1m", "fifteen minutes")) {
            ConfigurationException failure = assertThrows(
                ConfigurationException.class,
                () -> loader.load(
                    Map.<String, Object>of(
                        "collection.filesystem-timeout",
                        value
                    )::get
                )
            );

            assertTrue(
                failure.getMessage().contains("collection.filesystem-timeout")
            );
        }
    }

    @Test
    void rejectsFilesystemTimeoutOverflowWithFullPath() {
        ExporterConfiguration configuration = loader.load(
            Map.<String, Object>of(
                "collection.filesystem-timeout",
                "9223372036854775807s"
            )::get
        );

        ConfigurationException failure = assertThrows(
            ConfigurationException.class,
            () -> validator.validate(configuration)
        );

        assertTrue(
            failure.getMessage().contains("collection.filesystem-timeout")
        );
        assertTrue(failure.getMessage().contains("millisecond range"));
    }

    @Test
    void rejectsOutOfRangeWorldSizeScanConcurrencyWithFullPath() {
        for (int value : List.of(0, -1, 9)) {
            ExporterConfiguration configuration = loader.load(
                Map.<String, Object>of(
                    "filesystem.world-size-scan-concurrency",
                    value
                )::get
            );

            ConfigurationException failure = assertThrows(
                ConfigurationException.class,
                () -> validator.validate(configuration)
            );

            assertTrue(
                failure.getMessage().contains(
                    "filesystem.world-size-scan-concurrency"
                )
            );
            assertTrue(failure.getMessage().contains("between 1 and 8"));
        }
    }

    @Test
    void rejectsWrongNewValueTypesWithFullPaths() {
        ConfigurationException timeoutFailure = assertThrows(
            ConfigurationException.class,
            () -> loader.load(
                Map.<String, Object>of(
                    "collection.filesystem-timeout",
                    900
                )::get
            )
        );
        ConfigurationException concurrencyFailure = assertThrows(
            ConfigurationException.class,
            () -> loader.load(
                Map.<String, Object>of(
                    "filesystem.world-size-scan-concurrency",
                    "2"
                )::get
            )
        );

        assertTrue(
            timeoutFailure.getMessage().contains("collection.filesystem-timeout")
        );
        assertTrue(
            concurrencyFailure.getMessage().contains(
                "filesystem.world-size-scan-concurrency"
            )
        );
    }

    @Test
    void rejectsInvalidSemanticValues() {
        Map<String, Object> invalidPort = Map.of("http.port", 70_000);
        ExporterConfiguration configuration = loader.load(invalidPort::get);

        assertThrows(
            ConfigurationException.class,
            () -> validator.validate(configuration)
        );
    }

    @Test
    void rejectsSubTickIntervalsWithTheFullConfigurationPath() {
        ExporterConfiguration configuration = loader.load(
            Map.<String, Object>of("collection.server-interval", "49ms")::get
        );

        ConfigurationException failure = assertThrows(
            ConfigurationException.class,
            () -> validator.validate(configuration)
        );

        assertTrue(failure.getMessage().contains("collection.server-interval"));
        assertTrue(failure.getMessage().contains("50ms"));
    }

    @Test
    void rejectsDurationOverflowDuringParsingWithTheFullPath() {
        ConfigurationException failure = assertThrows(
            ConfigurationException.class,
            () -> loader.load(
                Map.<String, Object>of(
                    "collection.filesystem-interval",
                    "9223372036854775807h"
                )::get
            )
        );

        assertTrue(
            failure.getMessage().contains("collection.filesystem-interval")
        );
        assertTrue(failure.getMessage().contains("supported range"));
    }

    @Test
    void rejectsDurationsThatOverflowSchedulerMilliseconds() {
        ExporterConfiguration configuration = loader.load(
            Map.<String, Object>of(
                "collection.timeout",
                "9223372036854775807s"
            )::get
        );

        ConfigurationException failure = assertThrows(
            ConfigurationException.class,
            () -> validator.validate(configuration)
        );

        assertTrue(failure.getMessage().contains("collection.timeout"));
        assertTrue(failure.getMessage().contains("millisecond range"));
    }

    @Test
    void rejectsBlankHostAndEveryOutOfRangePort() {
        ExporterConfiguration blankHost = loader.load(
            Map.<String, Object>of("http.bind-address", "  ")::get
        );
        ExporterConfiguration zeroPort = loader.load(
            Map.<String, Object>of("http.port", 0)::get
        );
        ExporterConfiguration negativePort = loader.load(
            Map.<String, Object>of("http.port", -1)::get
        );

        assertThrows(ConfigurationException.class, () -> validator.validate(blankHost));
        assertThrows(ConfigurationException.class, () -> validator.validate(zeroPort));
        assertThrows(
            ConfigurationException.class,
            () -> validator.validate(negativePort)
        );
    }

    @Test
    void individualPlayerMetricsCannotBeEnabled() {
        Map<String, Object> values = Map.of(
            "privacy.individual-player-metrics-supported",
            true
        );
        ExporterConfiguration configuration = loader.load(values::get);

        assertThrows(
            ConfigurationException.class,
            () -> validator.validate(configuration)
        );
    }

    @Test
    void loadsPhaseSixKeysAndPrefersThemOverLegacyAliases() {
        Map<String, Object> values = new HashMap<>();
        values.put("collectors.folia", false);
        values.put("collectors.folia-regions", true);
        values.put("collection.folia-interval", "7s");
        values.put("collection.region-interval", "9s");
        values.put("folia.tps-windows", List.of("15s", "1m"));
        values.put("folia.tps.windows", List.of("5s"));
        values.put("folia.tps-thresholds", List.of(19, 17.5));
        values.put("folia.tps.thresholds", List.of(10));

        ExporterConfiguration configuration = loader.load(values::get);

        assertFalse(configuration.collectors().folia());
        assertEquals(Duration.ofSeconds(7), configuration.collection().foliaInterval());
        assertEquals(List.of("15s", "1m"), configuration.folia().tps().windows());
        assertEquals(List.of(19.0, 17.5), configuration.folia().tps().thresholds());
        assertDoesNotThrow(() -> validator.validate(configuration));
    }

    @Test
    void legacyPhaseSixKeysRemainCompatible() {
        Map<String, Object> values = Map.of(
            "collectors.folia-regions",
            false,
            "collection.region-interval",
            "6s",
            "folia.tps.windows",
            List.of("5s", "5m"),
            "folia.tps.thresholds",
            List.of(18)
        );

        ExporterConfiguration configuration = loader.load(values::get);

        assertFalse(configuration.collectors().folia());
        assertEquals(Duration.ofSeconds(6), configuration.collection().foliaInterval());
        assertDoesNotThrow(() -> validator.validate(configuration));
    }

    @Test
    void rejectsUnsupportedAndDuplicateFoliaWindows() {
        for (List<String> windows : List.of(
            List.of("30s"),
            List.of("5s", "5s")
        )) {
            ExporterConfiguration configuration = loader.load(
                Map.<String, Object>of("folia.tps-windows", windows)::get
            );
            ConfigurationException failure = assertThrows(
                ConfigurationException.class,
                () -> validator.validate(configuration)
            );
            assertTrue(failure.getMessage().contains("folia.tps-windows"));
        }
    }

    @Test
    void rejectsDuplicateNonFiniteAndOutOfRangeFoliaThresholds() {
        for (List<Double> thresholds : List.of(
            List.of(18.0, 18.0),
            List.of(Double.NaN),
            List.of(Double.POSITIVE_INFINITY),
            List.of(0.0),
            List.of(20.1)
        )) {
            ExporterConfiguration configuration = loader.load(
                Map.<String, Object>of(
                    "folia.tps-thresholds",
                    thresholds
                )::get
            );
            ConfigurationException failure = assertThrows(
                ConfigurationException.class,
                () -> validator.validate(configuration)
            );
            assertTrue(failure.getMessage().contains("folia.tps-thresholds"));
        }
    }

    @Test
    void rejectsFoliaTtlShorterThanCollectionIntervalAndOverflow() {
        ExporterConfiguration shortTtl = loader.load(
            Map.<String, Object>of("folia.observation-ttl", "4s")::get
        );
        ExporterConfiguration overflow = loader.load(
            Map.<String, Object>of(
                "folia.observation-ttl",
                "9223372036854775807s"
            )::get
        );

        assertThrows(ConfigurationException.class, () -> validator.validate(shortTtl));
        ConfigurationException failure = assertThrows(
            ConfigurationException.class,
            () -> validator.validate(overflow)
        );
        assertTrue(failure.getMessage().contains("folia.observation-ttl"));
    }
}
