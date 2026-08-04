package de.minecraftgilde.prometheus.minecraft.entity;

import static de.minecraftgilde.prometheus.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.MetricsCore;
import de.minecraftgilde.prometheus.collector.CollectorState;
import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.CollectorsConfiguration;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.EntitiesConfiguration;
import de.minecraftgilde.prometheus.scheduler.ManualCollectionScheduler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;

class PhaseSevenRuntimeTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-04T15:00:00Z"),
        ZoneOffset.UTC
    );

    @Test
    void enabledRuntimeRegistersOneListenerAndPublishesInitialZeroGroups() {
        MetricsCore core = core();
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        TestRegistration registration = new TestRegistration();
        World world = proxy(
            World.class,
            Map.of("getName", "world", "getLoadedChunks", new Chunk[0])
        );
        Server server = proxy(Server.class, Map.of("getWorlds", List.of(world)));
        AtomicInteger nanos = new AtomicInteger();
        PhaseSevenRuntime runtime = new PhaseSevenRuntime(
            core,
            server,
            scheduler,
            registration,
            configuration(true, false, false),
            CLOCK,
            (LongSupplier) () -> nanos.getAndAdd(1_000_000),
            Logger.getAnonymousLogger()
        );
        try {
            core.collectorCoordinator().startAll();
            scheduler.runGlobal();

            assertEquals(
                CollectorState.RUNNING,
                core.collectorCoordinator().states().get("entities")
            );
            assertEquals(1, registration.registered.get());
            assertEquals(
                10,
                metric(core, "minecraft_entity_group_count")
                    .getDataPoints()
                    .size()
            );
            assertTrue(names(core).contains("minecraft_world_entities"));
            assertFalse(names(core).contains("minecraft_entities"));
            assertFalse(names(core).contains("minecraft_world_projectiles"));
            assertTrue(names(core).contains(
                "minecraft_entity_reconciliation_duration_seconds"
            ));
            assertTrue(names(core).contains(
                "minecraft_entity_reconciliation_corrections_total"
            ));
        } finally {
            core.collectorCoordinator().stopAll();
            runtime.close();
            core.close();
        }
        assertEquals(1, registration.unregistered.get());
    }

    @Test
    void disabledRuntimeRegistersNoListenerTaskOrEntityFamily() {
        MetricsCore core = core();
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        TestRegistration registration = new TestRegistration();
        Server server = proxy(Server.class, Map.of("getWorlds", List.of()));
        PhaseSevenRuntime runtime = new PhaseSevenRuntime(
            core,
            server,
            scheduler,
            registration,
            configuration(false, true, true),
            CLOCK,
            System::nanoTime,
            Logger.getAnonymousLogger()
        );
        try {
            core.collectorCoordinator().startAll();

            assertEquals(
                CollectorState.DISABLED,
                core.collectorCoordinator().states().get("entities")
            );
            assertEquals(0, registration.registered.get());
            assertEquals(0, scheduler.activeGlobalTasks());
            assertTrue(names(core).stream().noneMatch(
                name -> name.startsWith("minecraft_entity")
                    || name.equals("minecraft_world_entities")
            ));
        } finally {
            core.collectorCoordinator().stopAll();
            runtime.close();
            core.close();
        }
    }

    private static MetricsCore core() {
        return new MetricsCore(
            "test",
            "unknown",
            "common",
            false,
            false,
            (name, failure) -> {}
        );
    }

    private static ExporterConfiguration configuration(
        boolean enabled,
        boolean exactTypes,
        boolean projectiles
    ) {
        ExporterConfiguration defaults = ExporterConfiguration.defaults();
        CollectorsConfiguration old = defaults.collectors();
        return new ExporterConfiguration(
            defaults.http(),
            defaults.collection(),
            new CollectorsConfiguration(
                old.server(),
                old.events(),
                old.worlds(),
                old.chunks(),
                enabled,
                old.foliaRegions(),
                false,
                false,
                old.filesystem(),
                old.exporter(),
                old.gameplay(),
                old.pluginInfo(),
                old.commands()
            ),
            defaults.folia(),
            new EntitiesConfiguration(
                Duration.ofMinutes(1),
                Duration.ofSeconds(30),
                exactTypes,
                projectiles
            ),
            defaults.filesystem(),
            defaults.privacy(),
            defaults.logging()
        );
    }

    private static List<String> names(MetricsCore core) {
        return core.registry().scrape()
            .stream()
            .map(snapshot -> snapshot
                .getMetadata()
                .getExpositionBasePrometheusName())
            .toList();
    }

    private static io.prometheus.metrics.model.snapshots.MetricSnapshot metric(
        MetricsCore core,
        String name
    ) {
        return core.registry().scrape()
            .stream()
            .filter(snapshot -> snapshot
                .getMetadata()
                .getExpositionBasePrometheusName()
                .equals(name))
            .findFirst()
            .orElseThrow();
    }

    private static final class TestRegistration
        implements EntityEventRegistration {

        private final AtomicInteger registered = new AtomicInteger();
        private final AtomicInteger unregistered = new AtomicInteger();

        @Override
        public void register(Listener listener) {
            registered.incrementAndGet();
        }

        @Override
        public void unregister(Listener listener) {
            unregistered.incrementAndGet();
        }
    }
}
