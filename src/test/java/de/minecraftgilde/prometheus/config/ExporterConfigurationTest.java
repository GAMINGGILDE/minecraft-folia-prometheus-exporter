package de.minecraftgilde.prometheus.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
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
        assertTrue(configuration.collectors().worlds());
        assertTrue(configuration.collectors().chunks());
        assertFalse(configuration.collectors().pluginInfo());
        assertTrue(configuration.filesystem().includeWorldSizes());
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
        values.put("collectors.jvm", false);
        values.put("collectors.process", false);
        values.put("collectors.server", false);
        values.put("collectors.worlds", false);
        values.put("collectors.chunks", true);
        values.put("collectors.plugin-info", true);
        values.put("filesystem.include-world-sizes", false);
        values.put("collectors.gameplay", true);
        values.put("logging.debug", true);

        ExporterConfiguration configuration = loader.load(values::get);

        assertEquals(12345, configuration.http().port());
        assertEquals(Duration.ofMillis(250), configuration.collection().timeout());
        assertFalse(configuration.privacy().individualPlayerMetricsSupported());
        assertFalse(configuration.collectors().jvm());
        assertFalse(configuration.collectors().process());
        assertFalse(configuration.collectors().server());
        assertFalse(configuration.collectors().worlds());
        assertTrue(configuration.collectors().chunks());
        assertTrue(configuration.collectors().pluginInfo());
        assertFalse(configuration.filesystem().includeWorldSizes());
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
}
