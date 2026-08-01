package de.minecraftgilde.prometheus.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.ExporterLifecycleState;
import de.minecraftgilde.prometheus.ExporterMetrics;
import de.minecraftgilde.prometheus.ExporterMetricsTestSupport;
import de.minecraftgilde.prometheus.collector.CollectorState;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.HttpConfiguration;
import de.minecraftgilde.prometheus.jvm.JvmMetricsRegistrar;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        }
    }

    @Test
    void handlesParallelRequestsAndReleasesThePortOnClose() throws Exception {
        TestServer testServer = startServer();
        int port = testServer.server.port();
        testServer.state.setInitializationComplete(true);

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<Integer>> responses = new ArrayList<>();
            for (int request = 0; request < 40; request++) {
                String path = request % 3 == 0 ? "/metrics" : "/health";
                responses.add(
                    executor.submit(() -> get(testServer.server, path).statusCode())
                );
            }
            for (Future<Integer> response : responses) {
                assertEquals(200, response.get());
            }
        }

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
        PrometheusRegistry registry = new PrometheusRegistry();
        new JvmMetricsRegistrar(registry, true, true).register();
        ExporterLifecycleState state = readyCoreState();
        ExporterMetrics metrics = ExporterMetricsTestSupport.create(registry);
        metrics.updateCollectorState("test-collector", CollectorState.STOPPED);
        MetricsHttpServer server = MetricsHttpServer.start(
            CONFIGURATION,
            registry,
            state,
            metrics
        );
        return new TestServer(server, state);
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
        ExporterLifecycleState state
    ) {}
}
