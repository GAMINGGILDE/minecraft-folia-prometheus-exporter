package de.minecraftgilde.prometheus;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Placeholder main class for the Folia-only exporter.
 *
 * <p>The implementation must follow the architecture and threading rules in
 * the docs directory. No individual player metrics may be added. Internal
 * Folia APIs are not permitted in version 1.</p>
 */
public final class FoliaPrometheusExporterPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info(
            "FoliaPrometheusExporter specification skeleton loaded. "
                + "Metrics implementation is not included yet."
        );
    }

    @Override
    public void onDisable() {
        // Future implementation must stop HTTP server and collector tasks here.
    }
}
