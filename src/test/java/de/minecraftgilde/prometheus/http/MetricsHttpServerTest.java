package de.minecraftgilde.prometheus.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.ExporterLifecycleState;
import de.minecraftgilde.prometheus.ExporterMetrics;
import de.minecraftgilde.prometheus.ExporterMetricsTestSupport;
import de.minecraftgilde.prometheus.TestConfigurations;
import de.minecraftgilde.prometheus.collector.CollectorState;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.HttpConfiguration;
import de.minecraftgilde.prometheus.jvm.JvmMetricsRegistrar;
import de.minecraftgilde.prometheus.minecraft.DifficultyLabel;
import de.minecraftgilde.prometheus.minecraft.EnvironmentLabel;
import de.minecraftgilde.prometheus.minecraft.GameModeLabel;
import de.minecraftgilde.prometheus.minecraft.PluginSnapshot;
import de.minecraftgilde.prometheus.minecraft.ServerSnapshot;
import de.minecraftgilde.prometheus.minecraft.WeatherLabel;
import de.minecraftgilde.prometheus.minecraft.WorldChunkSnapshot;
import de.minecraftgilde.prometheus.minecraft.WorldSizeSnapshot;
import de.minecraftgilde.prometheus.minecraft.WorldSnapshot;
import de.minecraftgilde.prometheus.minecraft.entity.EntityGroup;
import de.minecraftgilde.prometheus.minecraft.entity.EntityWorldSnapshot;
import de.minecraftgilde.prometheus.minecraft.metrics.EntityMetricsCollector;
import de.minecraftgilde.prometheus.minecraft.metrics.MinecraftMetrics;
import de.minecraftgilde.prometheus.minecraft.event.EventMetrics;
import de.minecraftgilde.prometheus.snapshot.ImmutableSnapshot;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bukkit.event.player.PlayerKickEvent;
import org.junit.jupiter.api.Test;

class MetricsHttpServerTest {

    private static final HttpConfiguration CONFIGURATION = new HttpConfiguration(
        "127.0.0.1",
        0,
        "/metrics",
        "/health",
        "/ready",
        4
    );

    @Test
    void servesMetricsHealthReadinessAndProtocolErrors() throws Exception {
        TestServer testServer = startServer();
        try (MetricsHttpServer server = testServer.server) {
            HttpResponse<String> notReady = get(server, "/ready");
            assertEquals(503, notReady.statusCode());
            assertEquals("not ready\n", notReady.body());

            HttpResponse<String> health = get(server, "/health");
            assertEquals(200, health.statusCode());
            assertEquals("ok\n", health.body());
            assertTrue(
                health.headers().firstValue("Content-Type").orElseThrow()
                    .startsWith("text/plain")
            );

            testServer.state.setInitializationComplete(true);
            HttpResponse<String> ready = get(server, "/ready");
            assertEquals(200, ready.statusCode());
            assertEquals("ready\n", ready.body());

            HttpResponse<String> unknown = get(server, "/unknown");
            assertEquals(404, unknown.statusCode());

            HttpResponse<String> wrongMethod = request(
                server,
                "/metrics",
                "POST"
            );
            assertEquals(405, wrongMethod.statusCode());
            assertEquals("GET", wrongMethod.headers().firstValue("Allow").orElseThrow());

            HttpResponse<String> metrics = get(server, "/metrics");
            assertEquals(200, metrics.statusCode());
            assertTrue(
                metrics.headers().firstValue("Content-Type").orElseThrow()
                    .contains("text/plain")
            );
            assertTrue(metrics.body().contains("minecraft_exporter_build_info"));
            assertTrue(metrics.body().contains("minecraft_exporter_ready 1.0"));
            assertTrue(metrics.body().contains("minecraft_exporter_health 1.0"));
            assertTrue(metrics.body().contains("minecraft_exporter_scrapes_total"));
            assertTrue(
                metrics.body().contains("minecraft_exporter_scrape_errors_total")
            );
            assertTrue(
                metrics.body().contains("minecraft_exporter_http_requests_total")
            );
            assertTrue(
                metrics.body().contains("minecraft_exporter_collector_state")
            );
            assertTrue(metrics.body().contains("jvm_memory_used_bytes"));
            assertTrue(metrics.body().contains("jvm_gc_collection_seconds_count"));
            assertTrue(metrics.body().contains("jvm_threads_current"));
            assertTrue(metrics.body().contains("jvm_classes_currently_loaded"));
            assertTrue(metrics.body().contains("jvm_buffer_pool_used_bytes"));
            assertTrue(metrics.body().contains("process_cpu_seconds_total"));
            assertSnapshotFamilies(metrics.body());
            assertEventFamilies(metrics.body());
            assertEntityFamilies(metrics.body());
            assertTrue(metrics.body().contains("minecraft_world_weather{weather=\"rain\",world=\"world\"} 1.0"));
            assertTrue(metrics.body().contains("minecraft_world_difficulty{difficulty=\"hard\",world=\"world\"} 1.0"));
            assertTrue(metrics.body().contains("minecraft_world_environment{environment=\"normal\",world=\"world\"} 1.0"));
            assertTrue(metrics.body().contains("minecraft_players_by_gamemode{gamemode=\"survival\"} 2.0"));
            assertTrue(!metrics.body().contains("minecraft_plugin_info"));
            assertTrue(!metrics.body().contains("Alice"));
            assertTrue(metrics.body().contains("minecraft_world_entities{world=\"world\"} 3.0"));
            assertTrue(!metrics.body().contains("minecraft_entities{"));
            assertTrue(!metrics.body().contains("minecraft_world_projectiles"));
            assertTrue(metrics.body().contains("minecraft_login_denied_total{reason=\"unknown\"} 1.0"));
            assertTrue(metrics.body().contains("minecraft_player_kicks_total{reason=\"connection_lost\"} 1.0"));
            assertReasonLabelsAreBounded(metrics.body());
            assertTrue(!metrics.body().contains("PRIVATE_LOGIN_REASON"));
            assertTrue(!metrics.body().contains("203.0.113.17"));
            assertTrue(!metrics.body().contains("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"));
            assertTrue(metrics.body().contains("minecraft_chunks_loaded_total{world=\"world\"} 2.0"));
            assertTrue(metrics.body().contains("minecraft_chunks_generated_total{world=\"world\"} 1.0"));
            assertTrue(!metrics.body().contains("minecraft_commands_total"));
            assertTrue(!metrics.body().contains("minecraft_chunk_load_failures_total"));
            assertTrue(!metrics.body().contains("minecraft_folia_"));
        }
    }

    @Test
    void optionalPluginInformationAppearsOnlyWhenEnabled() throws Exception {
        TestServer testServer = startServer(true);
        try (MetricsHttpServer server = testServer.server) {
            testServer.state.setInitializationComplete(true);

            String metrics = get(server, "/metrics").body();

            assertTrue(metrics.contains("# HELP minecraft_plugin_info "));
            assertTrue(metrics.contains("# TYPE minecraft_plugin_info gauge"));
            assertTrue(
                metrics.contains(
                    "minecraft_plugin_info{enabled=\"true\",name=\"Exporter\",version=\"1.0\"} 1"
                )
            );
        }
    }

    @Test
    void scrapesReadSnapshotsWithoutTriggeringCollection() throws Exception {
        TestServer testServer = startServer();
        try (MetricsHttpServer server = testServer.server) {
            Instant capturedAt = testServer.minecraftMetrics
                .serverRepository()
                .capturedAt()
                .orElseThrow();

            for (int attempt = 0; attempt < 5; attempt++) {
                assertEquals(200, get(server, "/metrics").statusCode());
            }

            assertEquals(
                capturedAt,
                testServer.minecraftMetrics
                    .serverRepository()
                    .capturedAt()
                    .orElseThrow()
            );
        }
    }

    @Test
    void handlesParallelRequestsAndReleasesThePortOnClose() throws Exception {
        TestServer testServer = startServer();
        int port = testServer.server.port();
        testServer.state.setInitializationComplete(true);

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> work = new ArrayList<>();
            for (int request = 0; request < 40; request++) {
                String path = request % 3 == 0 ? "/metrics" : "/health";
                work.add(executor.submit(() -> {
                    assertEquals(200, get(testServer.server, path).statusCode());
                    return null;
                }));
            }
            for (int event = 0; event < 1_000; event++) {
                work.add(executor.submit(() -> {
                    testServer.eventMetrics.recordJoin();
                    testServer.eventMetrics.recordChunkLoad("world", false);
                }));
            }
            for (Future<?> completed : work) {
                completed.get();
            }
        }

        String afterParallelWork = get(testServer.server, "/metrics").body();
        assertTrue(afterParallelWork.contains("minecraft_player_joins_total 1001.0"));
        assertTrue(afterParallelWork.contains("minecraft_chunks_loaded_total{world=\"world\"} 1002.0"));

        testServer.server.close();
        testServer.server.close();

        try (
            ServerSocket rebound = new ServerSocket()
        ) {
            rebound.bind(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port)
            );
            assertTrue(rebound.isBound());
        }
    }

    @Test
    void reportsAnOccupiedPortAsAControlledStartupFailure() throws Exception {
        try (
            ServerSocket occupied = new ServerSocket(
                0,
                1,
                InetAddress.getByName("127.0.0.1")
            )
        ) {
            HttpConfiguration occupiedConfiguration = new HttpConfiguration(
                "127.0.0.1",
                occupied.getLocalPort(),
                "/metrics",
                "/health",
                "/ready",
                1
            );
            PrometheusRegistry registry = new PrometheusRegistry();
            ExporterLifecycleState state = readyCoreState();
            ExporterMetrics metrics = ExporterMetricsTestSupport.create(registry);

            assertThrows(
                IOException.class,
                () -> MetricsHttpServer.start(
                    occupiedConfiguration,
                    registry,
                    state,
                    metrics
                )
            );
        }
    }

    private static TestServer startServer() throws IOException {
        return startServer(false);
    }

    private static TestServer startServer(boolean pluginInfo) throws IOException {
        PrometheusRegistry registry = new PrometheusRegistry();
        new JvmMetricsRegistrar(registry, true, true).register();
        MinecraftMetrics minecraftMetrics = new MinecraftMetrics(
            registry,
            TestConfigurations.snapshotCollectors(true, true, true, true, pluginInfo)
        );
        minecraftMetrics.register();
        publishMinecraftSnapshots(minecraftMetrics);
        EventMetrics eventMetrics = new EventMetrics(registry);
        publishEvents(eventMetrics);
        publishEntitySnapshot(registry);
        ExporterLifecycleState state = readyCoreState();
        ExporterMetrics metrics = ExporterMetricsTestSupport.create(registry);
        metrics.updateCollectorState("test-collector", CollectorState.STOPPED);
        MetricsHttpServer server = MetricsHttpServer.start(
            CONFIGURATION,
            registry,
            state,
            metrics
        );
        return new TestServer(server, state, minecraftMetrics, eventMetrics);
    }

    private static void publishEvents(EventMetrics metrics) {
        metrics.recordLogin("ALLOWED");
        metrics.recordLogin("PRIVATE_LOGIN_REASON");
        metrics.recordJoin();
        metrics.recordQuit();
        metrics.recordKick(PlayerKickEvent.Cause.TIMEOUT);
        metrics.recordServerListPing();
        metrics.recordChatMessage();
        metrics.recordChunkLoad("world", false);
        metrics.recordChunkLoad("world", true);
        metrics.recordChunkUnload("world");
    }

    private static void publishMinecraftSnapshots(MinecraftMetrics metrics) {
        Instant capturedAt = Instant.parse("2026-08-02T10:00:00Z");
        EnumMap<GameModeLabel, Integer> gameModes = new EnumMap<>(
            GameModeLabel.class
        );
        gameModes.put(GameModeLabel.SURVIVAL, 2);
        metrics.serverRepository().publish(
            new ImmutableSnapshot<>(
                capturedAt,
                List.of(
                    new ServerSnapshot(
                        "Paper",
                        "26.1.2",
                        "25",
                        5,
                        1,
                        true,
                        false,
                        10,
                        8,
                        2,
                        100,
                        3,
                        1,
                        1,
                        1,
                        gameModes,
                        1,
                        1,
                        0,
                        List.of(new PluginSnapshot("Exporter", "1.0", true))
                    )
                )
            )
        );
        metrics.worldRepository().publish(
            new ImmutableSnapshot<>(
                capturedAt,
                List.of(
                    new WorldSnapshot(
                        "world",
                        2,
                        6000,
                        1000,
                        WeatherLabel.RAIN,
                        DifficultyLabel.HARD,
                        EnvironmentLabel.NORMAL,
                        true
                    )
                )
            )
        );
        metrics.chunkRepository().publish(
            new ImmutableSnapshot<>(
                capturedAt,
                List.of(new WorldChunkSnapshot("world", 12))
            )
        );
        metrics.worldSizeRepository().publish(
            new ImmutableSnapshot<>(
                capturedAt,
                List.of(new WorldSizeSnapshot("world", 1234))
            )
        );
    }

    private static void publishEntitySnapshot(PrometheusRegistry registry) {
        SnapshotRepository<EntityWorldSnapshot> repository =
            new SnapshotRepository<>();
        registry.register(new EntityMetricsCollector(repository, false, false));
        EnumMap<EntityGroup, Long> groups = new EnumMap<>(EntityGroup.class);
        for (EntityGroup group : EntityGroup.values()) {
            groups.put(group, 0L);
        }
        groups.put(EntityGroup.MONSTER, 2L);
        groups.put(EntityGroup.ITEM, 1L);
        repository.publish(new ImmutableSnapshot<>(
            Instant.parse("2026-08-04T15:00:00Z"),
            List.of(new EntityWorldSnapshot(
                "world",
                groups,
                3L,
                2L,
                0L,
                1L,
                0L,
                java.util.Map.of()
            ))
        ));
    }

    private static void assertSnapshotFamilies(String metrics) {
        for (String family : List.of(
            "minecraft_server_info",
            "minecraft_server_uptime_seconds",
            "minecraft_players_online",
            "minecraft_plugins_total",
            "minecraft_world_players",
            "minecraft_world_loaded_chunks",
            "minecraft_world_size_bytes",
            "minecraft_world_time_ticks",
            "minecraft_world_weather"
        )) {
            assertTrue(metrics.contains("# HELP " + family + " "), family);
            assertTrue(metrics.contains("# TYPE " + family + " "), family);
            assertTrue(metrics.contains("\n" + family), family);
        }
    }

    private static void assertEventFamilies(String metrics) {
        for (String family : List.of(
            "minecraft_login_attempts_total",
            "minecraft_login_denied_total",
            "minecraft_player_joins_total",
            "minecraft_player_quits_total",
            "minecraft_player_kicks_total",
            "minecraft_server_list_pings_total",
            "minecraft_chat_messages_total",
            "minecraft_chunks_loaded_total",
            "minecraft_chunks_unloaded_total",
            "minecraft_chunks_generated_total"
        )) {
            assertTrue(metrics.contains("# HELP " + family + " "), family);
            assertTrue(metrics.contains("# TYPE " + family + " counter"), family);
            assertTrue(metrics.contains("\n" + family), family);
        }
        assertTrue(!metrics.contains("private chat content"));
        assertTrue(!metrics.contains("private-client-host"));
    }

    private static void assertEntityFamilies(String metrics) {
        for (String family : List.of(
            "minecraft_entity_group_count",
            "minecraft_world_entities",
            "minecraft_world_living_entities",
            "minecraft_world_villagers",
            "minecraft_world_item_entities"
        )) {
            assertTrue(metrics.contains("# HELP " + family + " "), family);
            assertTrue(metrics.contains("# TYPE " + family + " gauge"), family);
            assertTrue(metrics.contains("\n" + family), family);
        }
        long groupSamples = metrics.lines()
            .filter(line -> line.startsWith("minecraft_entity_group_count{"))
            .count();
        assertEquals(EntityGroup.values().length, groupSamples);
        assertTrue(!metrics.contains("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"));
        assertTrue(!metrics.contains("chunk_x"));
        assertTrue(!metrics.contains("chunk_z"));
    }

    private static void assertReasonLabelsAreBounded(String metrics) {
        java.util.Set<String> allowed = java.util.Set.of(
            "banned",
            "whitelist",
            "server_full",
            "invalid_session",
            "idle",
            "connection_lost",
            "moderation",
            "plugin",
            "unknown"
        );
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("reason=\\\"([^\\\"]*)\\\"")
            .matcher(metrics);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            assertTrue(allowed.contains(matcher.group(1)), matcher.group(1));
        }
        assertTrue(found);
    }

    private static ExporterLifecycleState readyCoreState() {
        ExporterLifecycleState state = new ExporterLifecycleState();
        state.setRegistryAvailable(true);
        state.setMetricsCoreStarted(true);
        return state;
    }

    private static HttpResponse<String> get(MetricsHttpServer server, String path)
        throws IOException, InterruptedException {
        return request(server, path, "GET");
    }

    private static HttpResponse<String> request(
        MetricsHttpServer server,
        String path,
        String method
    ) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + server.port() + path))
            .timeout(Duration.ofSeconds(5))
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private record TestServer(
        MetricsHttpServer server,
        ExporterLifecycleState state,
        MinecraftMetrics minecraftMetrics,
        EventMetrics eventMetrics
    ) {}
}
