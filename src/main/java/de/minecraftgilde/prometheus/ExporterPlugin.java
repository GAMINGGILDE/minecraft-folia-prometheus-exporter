package de.minecraftgilde.prometheus;

import de.minecraftgilde.prometheus.config.ConfigurationLoader;
import de.minecraftgilde.prometheus.config.ConfigurationValidator;
import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point for the Paper and Folia plugin. */
public final class ExporterPlugin extends JavaPlugin {

    private final ConfigurationLoader configurationLoader;
    private final ConfigurationValidator configurationValidator;
    private ExporterConfiguration configuration;
    private MetricsCore metricsCore;

    public ExporterPlugin() {
        this(new ConfigurationLoader(), new ConfigurationValidator());
    }

    ExporterPlugin(
        ConfigurationLoader configurationLoader,
        ConfigurationValidator configurationValidator
    ) {
        this.configurationLoader = Objects.requireNonNull(
            configurationLoader,
            "configurationLoader"
        );
        this.configurationValidator = Objects.requireNonNull(
            configurationValidator,
            "configurationValidator"
        );
    }

    @Override
    public void onEnable() {
        MetricsCore initializingCore = null;
        try {
            saveDefaultConfig();
            ExporterConfiguration loaded = configurationLoader.load(
                path -> getConfig().get(path)
            );
            configurationValidator.validate(loaded);
            configuration = loaded;

            initializingCore = new MetricsCore(
                getPluginMeta().getVersion(),
                "unknown",
                "common",
                (collector, failure) -> getLogger().log(
                    Level.WARNING,
                    "Collector '" + collector + "' failed; other collectors remain active.",
                    failure
                )
            );
            metricsCore = initializingCore;
            initializingCore.start(loaded.http());
        } catch (Exception exception) {
            closeQuietly(initializingCore);
            metricsCore = null;
            getLogger().log(
                Level.SEVERE,
                "FoliaPrometheusExporter could not start: " + exception.getMessage(),
                exception
            );
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("FoliaPrometheusExporter started.");
    }

    @Override
    public void onDisable() {
        MetricsCore core = metricsCore;
        metricsCore = null;
        closeQuietly(core);
        configuration = null;
        getLogger().info("FoliaPrometheusExporter stopped.");
    }

    private static void closeQuietly(MetricsCore core) {
        if (core != null) {
            core.close();
        }
    }
}
