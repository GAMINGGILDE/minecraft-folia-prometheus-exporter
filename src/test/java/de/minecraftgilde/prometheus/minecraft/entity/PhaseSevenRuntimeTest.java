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
import java.util.concurrent.atomic.AtomicReference;
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

    @Test
    void initialWorldEnumerationFailurePublishesNoArtificialZeroRows() {
        MetricsCore core = core();
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        TestRegistration registration = new TestRegistration();
        World broken = proxy(
            World.class,
            Map.of(
                "getName",
                "world",
                "getLoadedChunks",
                (java.util.function.Function<Object[], Object>) ignored -> {
                    throw new IllegalStateException("technical details");
                }
            )
        );
        PhaseSevenRuntime runtime = new PhaseSevenRuntime(
            core,
            proxy(Server.class, Map.of("getWorlds", List.of(broken))),
            scheduler,
            registration,
            configuration(true, false, false),
            CLOCK,
            System::nanoTime,
            Logger.getAnonymousLogger()
        );
        try {
            core.collectorCoordinator().startAll();
            scheduler.runGlobal();

            assertTrue(metric(core, "minecraft_entity_group_count")
                .getDataPoints()
                .isEmpty());
            assertTrue(metric(core, "minecraft_world_entities")
                .getDataPoints()
                .isEmpty());
            assertEquals(
                0.0,
                value(core, "minecraft_entity_reconciliation_duration_seconds")
            );
            assertEquals(
                0.0,
                value(
                    core,
                    "minecraft_entity_reconciliation_last_success_timestamp_seconds"
                )
            );
            assertEquals(
                CollectorState.RUNNING,
                core.collectorCoordinator().states().get("entities")
            );
        } finally {
            core.collectorCoordinator().stopAll();
            runtime.close();
            core.close();
        }
    }

    @Test
    void laterWorldFailureRetainsTheLastValueAndSuccessMetrics() {
        MetricsCore core = core();
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        TestRegistration registration = new TestRegistration();
        AtomicReference<Boolean> fail = new AtomicReference<>(false);
        World[] worldHolder = new World[1];
        org.bukkit.entity.Entity zombie = proxy(
            org.bukkit.entity.LivingEntity.class,
            Map.of(
                "getWorld",
                (java.util.function.Function<Object[], Object>) ignored ->
                    worldHolder[0],
                "getType",
                org.bukkit.entity.EntityType.ZOMBIE,
                "getUniqueId",
                new java.util.UUID(0L, 99L)
            )
        );
        Chunk chunk = proxy(
            Chunk.class,
            Map.of(
                "getX",
                0,
                "getZ",
                0,
                "getWorld",
                (java.util.function.Function<Object[], Object>) ignored ->
                    worldHolder[0],
                "isLoaded",
                true,
                "isEntitiesLoaded",
                true,
                "getEntities",
                new org.bukkit.entity.Entity[] { zombie }
            )
        );
        World world = proxy(
            World.class,
            Map.of(
                "getName",
                "world",
                "getLoadedChunks",
                (java.util.function.Function<Object[], Object>) ignored -> {
                    if (fail.get()) {
                        throw new IllegalStateException("temporary");
                    }
                    return new Chunk[] { chunk };
                }
            )
        );
        worldHolder[0] = world;
        AtomicInteger nanos = new AtomicInteger();
        PhaseSevenRuntime runtime = new PhaseSevenRuntime(
            core,
            proxy(Server.class, Map.of("getWorlds", List.of(world))),
            scheduler,
            registration,
            configuration(true, false, false),
            CLOCK,
            () -> nanos.getAndAdd(1_000_000),
            Logger.getAnonymousLogger()
        );
        try {
            core.collectorCoordinator().startAll();
            scheduler.runGlobal();
            double duration = value(
                core,
                "minecraft_entity_reconciliation_duration_seconds"
            );
            double lastSuccess = value(
                core,
                "minecraft_entity_reconciliation_last_success_timestamp_seconds"
            );
            assertEquals(1.0, value(core, "minecraft_world_entities"));

            fail.set(true);
            scheduler.runGlobal();

            assertEquals(1.0, value(core, "minecraft_world_entities"));
            assertEquals(
                duration,
                value(core, "minecraft_entity_reconciliation_duration_seconds")
            );
            assertEquals(
                lastSuccess,
                value(
                    core,
                    "minecraft_entity_reconciliation_last_success_timestamp_seconds"
                )
            );
            assertEquals(
                0.0,
                value(core, "minecraft_entity_reconciliation_corrections_total")
            );
        } finally {
            core.collectorCoordinator().stopAll();
            runtime.close();
            core.close();
        }
    }

    @Test
    void genuinelyEmptyGlobalWorldListRemovesPreviouslyPublishedWorlds() {
        MetricsCore core = core();
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        TestRegistration registration = new TestRegistration();
        World world = proxy(
            World.class,
            Map.of("getName", "world", "getLoadedChunks", new Chunk[0])
        );
        AtomicReference<List<World>> worlds = new AtomicReference<>(List.of(world));
        Server server = proxy(
            Server.class,
            Map.of(
                "getWorlds",
                (java.util.function.Function<Object[], Object>) ignored -> worlds.get()
            )
        );
        PhaseSevenRuntime runtime = new PhaseSevenRuntime(
            core,
            server,
            scheduler,
            registration,
            configuration(true, false, false),
            CLOCK,
            System::nanoTime,
            Logger.getAnonymousLogger()
        );
        try {
            core.collectorCoordinator().startAll();
            scheduler.runGlobal();
            assertEquals(10, metric(core, "minecraft_entity_group_count")
                .getDataPoints()
                .size());

            worlds.set(List.of());
            scheduler.runGlobal();

            assertTrue(metric(core, "minecraft_entity_group_count")
                .getDataPoints()
                .isEmpty());
            assertTrue(metric(core, "minecraft_world_entities")
                .getDataPoints()
                .isEmpty());
        } finally {
            core.collectorCoordinator().stopAll();
            runtime.close();
            core.close();
        }
    }

    @Test
    void technicalGlobalWorldListFailureRetainsTheFullPreviousSnapshot() {
        MetricsCore core = core();
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        TestRegistration registration = new TestRegistration();
        AtomicReference<Boolean> fail = new AtomicReference<>(false);
        World world = proxy(
            World.class,
            Map.of("getName", "world", "getLoadedChunks", new Chunk[0])
        );
        Server server = proxy(
            Server.class,
            Map.of(
                "getWorlds",
                (java.util.function.Function<Object[], Object>) ignored -> {
                    if (fail.get()) {
                        throw new IllegalStateException("technical failure");
                    }
                    return List.of(world);
                }
            )
        );
        PhaseSevenRuntime runtime = new PhaseSevenRuntime(
            core,
            server,
            scheduler,
            registration,
            configuration(true, false, false),
            CLOCK,
            System::nanoTime,
            Logger.getAnonymousLogger()
        );
        try {
            core.collectorCoordinator().startAll();
            scheduler.runGlobal();
            double lastSuccess = value(
                core,
                "minecraft_entity_reconciliation_last_success_timestamp_seconds"
            );

            fail.set(true);
            scheduler.runGlobal();

            assertEquals(10, metric(core, "minecraft_entity_group_count")
                .getDataPoints()
                .size());
            assertEquals(
                lastSuccess,
                value(
                    core,
                    "minecraft_entity_reconciliation_last_success_timestamp_seconds"
                )
            );
            assertEquals(
                CollectorState.RUNNING,
                core.collectorCoordinator().states().get("entities")
            );
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

    private static double value(MetricsCore core, String name) {
        io.prometheus.metrics.model.snapshots.MetricSnapshot snapshot = metric(
            core,
            name
        );
        if (snapshot instanceof io.prometheus.metrics.model.snapshots.GaugeSnapshot gauge) {
            return gauge.getDataPoints().getFirst().getValue();
        }
        return ((io.prometheus.metrics.model.snapshots.CounterSnapshot) snapshot)
            .getDataPoints()
            .getFirst()
            .getValue();
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
