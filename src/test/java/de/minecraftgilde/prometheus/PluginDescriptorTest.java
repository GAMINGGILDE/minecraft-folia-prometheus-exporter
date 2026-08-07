package de.minecraftgilde.prometheus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.config.ConfigurationLoader;
import de.minecraftgilde.prometheus.config.ConfigurationValidator;
import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {

    @Test
    void descriptorContainsExpandedMetadata() throws IOException {
        String descriptor = resourceText("plugin.yml");

        assertTrue(descriptor.contains("name: FoliaPrometheusExporter"));
        assertTrue(descriptor.contains("version: '1.0.1'"));
        assertTrue(descriptor.contains("main: de.minecraftgilde.prometheus.ExporterPlugin"));
        assertTrue(descriptor.contains("api-version: '26.1.2'"));
        assertTrue(descriptor.contains("folia-supported: true"));
        assertFalse(descriptor.contains("${version}"));
        assertFalse(descriptor.contains("${"));
        assertNull(
            PluginDescriptorTest.class.getClassLoader().getResource("paper-plugin.yml")
        );
    }

    @Test
    void defaultConfigurationContainsOnlyFunctionalOptions() throws IOException {
        String configuration = resourceText("config.yml");

        assertFalse(configuration.contains("experimental-internal-provider"));
        assertTrue(
            configuration.contains("individual-player-metrics-supported: false")
        );
        assertTrue(configuration.contains("plugin-info: false"));
        assertTrue(configuration.contains("collection-errors: true"));
        for (String removed : List.of(
            "  exporter:",
            "  gameplay:",
            "  commands:",
            "  include-server-filesystem:",
            "  include-log-size:",
            "  include-plugin-size:",
            "  debug:"
        )) {
            assertFalse(
                configuration.contains(removed),
                "Removed option remains in config.yml: " + removed.trim()
            );
        }
    }

    @Test
    void bundledDefaultConfigurationLoadsAndValidates() throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
            new StringReader(resourceText("config.yml"))
        );
        ExporterConfiguration configuration = new ConfigurationLoader().load(
            yaml::get
        );

        assertEquals(ExporterConfiguration.defaults(), configuration);
        assertDoesNotThrow(
            () -> new ConfigurationValidator().validate(configuration)
        );
    }

    private static String resourceText(String name) throws IOException {
        try (
            InputStream input = PluginDescriptorTest.class
                .getClassLoader()
                .getResourceAsStream(name)
        ) {
            assertNotNull(input, "Missing test resource: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
