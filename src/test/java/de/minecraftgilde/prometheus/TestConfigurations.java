package de.minecraftgilde.prometheus;

import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.CollectorsConfiguration;

/** Immutable configuration variants used by integration-style unit tests. */
public final class TestConfigurations {

    private TestConfigurations() {}

    public static ExporterConfiguration snapshotCollectors(
        boolean server,
        boolean worlds,
        boolean chunks,
        boolean filesystem,
        boolean pluginInfo
    ) {
        ExporterConfiguration defaults = ExporterConfiguration.defaults();
        CollectorsConfiguration current = defaults.collectors();
        CollectorsConfiguration collectors = new CollectorsConfiguration(
            server,
            current.events(),
            worlds,
            chunks,
            current.entities(),
            current.foliaRegions(),
            current.jvm(),
            current.process(),
            filesystem,
            pluginInfo
        );
        return new ExporterConfiguration(
            defaults.http(),
            defaults.collection(),
            collectors,
            defaults.folia(),
            defaults.entities(),
            defaults.filesystem(),
            defaults.privacy(),
            defaults.logging()
        );
    }
}
