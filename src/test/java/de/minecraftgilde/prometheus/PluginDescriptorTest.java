package de.minecraftgilde.prometheus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {

    @Test
    void descriptorContainsExpandedMetadata() throws IOException {
        String descriptor = resourceText("plugin.yml");

        assertTrue(descriptor.contains("name: FoliaPrometheusExporter"));
        assertTrue(descriptor.contains("version: '1.0.0'"));
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
    void defaultConfigurationDoesNotExposeInternalProviders() throws IOException {
        String configuration = resourceText("config.yml");

        assertFalse(configuration.contains("experimental-internal-provider"));
        assertTrue(
            configuration.contains("individual-player-metrics-supported: false")
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
