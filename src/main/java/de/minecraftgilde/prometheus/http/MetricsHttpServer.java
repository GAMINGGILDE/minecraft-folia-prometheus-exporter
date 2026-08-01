package de.minecraftgilde.prometheus.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.minecraftgilde.prometheus.ExporterLifecycleState;
import de.minecraftgilde.prometheus.ExporterMetrics;
import de.minecraftgilde.prometheus.config.ExporterConfiguration.HttpConfiguration;
import io.prometheus.metrics.exporter.httpserver.MetricsHandler;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Embedded JDK HTTP server using the official Prometheus scrape handler. */
public final class MetricsHttpServer implements AutoCloseable {

    private static final String TEXT_CONTENT_TYPE = "text/plain; charset=utf-8";

    private final HttpServer server;
    private final ExecutorService executor;
    private final ExporterLifecycleState lifecycleState;
    private final ExporterMetrics exporterMetrics;
    private final AtomicBoolean closed = new AtomicBoolean();

    private MetricsHttpServer(
        HttpServer server,
        ExecutorService executor,
        ExporterLifecycleState lifecycleState,
        ExporterMetrics exporterMetrics
    ) {
        this.server = server;
        this.executor = executor;
        this.lifecycleState = lifecycleState;
        this.exporterMetrics = exporterMetrics;
    }

    public static MetricsHttpServer start(
        HttpConfiguration configuration,
        PrometheusRegistry registry,
        ExporterLifecycleState lifecycleState,
        ExporterMetrics exporterMetrics
    ) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(lifecycleState, "lifecycleState");
        Objects.requireNonNull(exporterMetrics, "exporterMetrics");

        InetAddress address = InetAddress.getByName(configuration.bindAddress());
        HttpServer server = HttpServer.create(
            new InetSocketAddress(address, configuration.port()),
            3
        );
        ExecutorService executor = Executors.newFixedThreadPool(
            configuration.workerThreads(),
            new HttpThreadFactory()
        );
        MetricsHttpServer result = new MetricsHttpServer(
            server,
            executor,
            lifecycleState,
            exporterMetrics
        );

        try {
            MetricsHandler metricsHandler = new MetricsHandler(registry);
            server.createContext(
                "/",
                exchange -> result.handle(exchange, configuration, metricsHandler)
            );
            server.setExecutor(executor);
            lifecycleState.setHttpServerRunning(true);
            exporterMetrics.setHealthy(true);
            server.start();
            return result;
        } catch (RuntimeException exception) {
            lifecycleState.setHttpServerRunning(false);
            exporterMetrics.setHealthy(false);
            server.stop(0);
            executor.shutdownNow();
            throw exception;
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        lifecycleState.setHttpServerRunning(false);
        exporterMetrics.setReady(false);
        exporterMetrics.setHealthy(false);
        server.stop(0);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void handle(
        HttpExchange exchange,
        HttpConfiguration configuration,
        MetricsHandler metricsHandler
    ) throws IOException {
        Endpoint endpoint = endpointFor(exchange, configuration);
        if (endpoint == Endpoint.NOT_FOUND) {
            sendText(exchange, 404, "not found\n");
            exporterMetrics.recordHttpRequest(endpoint.metricValue, 404);
            return;
        }

        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendText(exchange, 405, "method not allowed\n");
            exporterMetrics.recordHttpRequest(endpoint.metricValue, 405);
            return;
        }

        switch (endpoint) {
            case METRICS -> handleMetrics(exchange, metricsHandler);
            case HEALTH -> {
                boolean healthy = lifecycleState.isHealthy();
                exporterMetrics.setHealthy(healthy);
                int status = healthy ? 200 : 503;
                sendText(exchange, status, healthy ? "ok\n" : "unhealthy\n");
                exporterMetrics.recordHttpRequest(endpoint.metricValue, status);
            }
            case READY -> {
                boolean ready = lifecycleState.isReady();
                exporterMetrics.setReady(ready);
                int status = ready ? 200 : 503;
                sendText(exchange, status, ready ? "ready\n" : "not ready\n");
                exporterMetrics.recordHttpRequest(endpoint.metricValue, status);
            }
            case NOT_FOUND -> throw new IllegalStateException("Handled above");
        }
    }

    private void handleMetrics(
        HttpExchange exchange,
        MetricsHandler metricsHandler
    ) throws IOException {
        exporterMetrics.recordScrapeAttempt();
        int status = 500;
        boolean errorRecorded = false;
        try {
            metricsHandler.handle(exchange);
            status = exchange.getResponseCode();
            if (status < 0) {
                status = 500;
            }
            if (status >= 500) {
                exporterMetrics.recordScrapeError();
                errorRecorded = true;
            }
        } catch (IOException | RuntimeException exception) {
            if (!errorRecorded) {
                exporterMetrics.recordScrapeError();
            }
            if (exchange.getResponseCode() < 0) {
                sendText(exchange, 500, "metrics scrape failed\n");
            } else {
                exchange.close();
            }
            throw exception;
        } finally {
            exporterMetrics.recordHttpRequest(Endpoint.METRICS.metricValue, status);
        }
    }

    private static Endpoint endpointFor(
        HttpExchange exchange,
        HttpConfiguration configuration
    ) {
        String path = exchange.getRequestURI().getPath();
        if (configuration.metricsPath().equals(path)) {
            return Endpoint.METRICS;
        }
        if (configuration.healthPath().equals(path)) {
            return Endpoint.HEALTH;
        }
        if (configuration.readyPath().equals(path)) {
            return Endpoint.READY;
        }
        return Endpoint.NOT_FOUND;
    }

    private static void sendText(HttpExchange exchange, int status, String text)
        throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", TEXT_CONTENT_TYPE);
        exchange.sendResponseHeaders(status, body.length);
        try (exchange; var responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
        }
    }

    private enum Endpoint {
        METRICS("metrics"),
        HEALTH("health"),
        READY("ready"),
        NOT_FOUND("not_found");

        private final String metricValue;

        Endpoint(String metricValue) {
            this.metricValue = metricValue;
        }
    }

    private static final class HttpThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(
                task,
                "folia-prometheus-http-" + sequence.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        }
    }
}
