package de.minecraftgilde.prometheus;

import de.minecraftgilde.prometheus.config.ConfigurationLoader;
import de.minecraftgilde.prometheus.config.ConfigurationValidator;
import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.folia.FoliaRuntime;
import de.minecraftgilde.prometheus.minecraft.MinecraftSnapshotRuntime;
import de.minecraftgilde.prometheus.minecraft.entity.EntityRuntime;
import de.minecraftgilde.prometheus.minecraft.event.EventRuntime;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point for the Paper and Folia plugin. */
public final class ExporterPlugin extends JavaPlugin {

    private final ConfigurationLoader configurationLoader;
    private final ConfigurationValidator configurationValidator;
    private ExporterConfiguration configuration;
    private MetricsCore metricsCore;
    private MinecraftSnapshotRuntime snapshotRuntime;
    private FoliaRuntime foliaRuntime;
    private EntityRuntime entityRuntime;

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
        Instant activationTime = Instant.now();
        MetricsCore initializingCore = null;
        MinecraftSnapshotRuntime initializingSnapshots = null;
        FoliaRuntime initializingFolia = null;
        EntityRuntime initializingEntities = null;
        try {
            saveDefaultConfig();
            ExporterConfiguration loaded = configurationLoader.load(
                path -> getConfig().get(path)
            );
            configurationValidator.validate(loaded);
            configuration = loaded;

            BuildInformation buildInformation = BuildInformation.load();
            initializingCore = new MetricsCore(
                getPluginMeta().getVersion(),
                buildInformation.gitCommit(),
                "common",
                loaded.collectors().jvm(),
                loaded.collectors().process(),
                (collector, failure) -> getLogger().log(
                    Level.WARNING,
                    "Collector '" + collector + "' failed; other collectors remain active.",
                    failure
                )
            );
            initializingSnapshots = new MinecraftSnapshotRuntime(
                initializingCore,
                this,
                loaded,
                activationTime,
                Clock.systemUTC()
            );
            new EventRuntime(
                initializingCore,
                this,
                loaded,
                Clock.systemUTC()
            );
            initializingFolia = new FoliaRuntime(
                initializingCore,
                this,
                loaded,
                Clock.systemUTC()
            );
            initializingEntities = new EntityRuntime(
                initializingCore,
                this,
                loaded,
                Clock.systemUTC()
            );
            metricsCore = initializingCore;
            snapshotRuntime = initializingSnapshots;
            foliaRuntime = initializingFolia;
            entityRuntime = initializingEntities;
            initializingCore.start(loaded.http());
        } catch (Exception exception) {
            closeQuietly(initializingCore);
            closeQuietly(initializingEntities);
            closeQuietly(initializingFolia);
            closeQuietly(initializingSnapshots);
            metricsCore = null;
            snapshotRuntime = null;
            foliaRuntime = null;
            entityRuntime = null;
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
        MinecraftSnapshotRuntime snapshots = snapshotRuntime;
        FoliaRuntime folia = foliaRuntime;
        EntityRuntime entities = entityRuntime;
        metricsCore = null;
        snapshotRuntime = null;
        foliaRuntime = null;
        entityRuntime = null;
        closeQuietly(core);
        closeQuietly(entities);
        closeQuietly(folia);
        closeQuietly(snapshots);
        configuration = null;
        getLogger().info("FoliaPrometheusExporter stopped.");
    }

    private static void closeQuietly(MetricsCore core) {
        if (core != null) {
            core.close();
        }
    }

    private static void closeQuietly(MinecraftSnapshotRuntime runtime) {
        if (runtime != null) {
            runtime.close();
        }
    }

    private static void closeQuietly(FoliaRuntime runtime) {
        if (runtime != null) {
            runtime.close();
        }
    }

    private static void closeQuietly(EntityRuntime runtime) {
        if (runtime != null) {
            runtime.close();
        }
    }
}
