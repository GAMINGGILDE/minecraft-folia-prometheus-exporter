package de.minecraftgilde.prometheus.folia.provider;

import de.minecraftgilde.prometheus.collector.PeriodicSnapshotCollector;
import de.minecraftgilde.prometheus.config.ExporterConfiguration;
import de.minecraftgilde.prometheus.folia.FoliaProvider;
import de.minecraftgilde.prometheus.folia.FoliaProviderContext;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Concrete provider compiled only against the pinned public Folia API. */
public final class FoliaRegionProvider implements FoliaProvider {

    private final RegionObservationRegistry observationRegistry =
        new RegionObservationRegistry();
    private final PeriodicSnapshotCollector<RegionObservation> collector;
    private final AtomicBoolean started = new AtomicBoolean();

    public FoliaRegionProvider(FoliaProviderContext context) {
        Objects.requireNonNull(context, "context");
        ExporterConfiguration configuration = context.configuration();
        List<TpsWindow> windows = TpsWindow.configured(
            configuration.folia().tps().windows()
        );
        List<String> statistics = orderedStatistics(
            configuration.folia().tps().statistics()
        );
        List<TpsThreshold> thresholds = TpsThreshold.configured(
            configuration.folia().tps().thresholds()
        );
        FoliaMetrics metrics = new FoliaMetrics(
            context.registry(),
            context.clock(),
            configuration.folia().observationTtl(),
            windows,
            statistics,
            thresholds
        );
        FoliaRegionSnapshotCapture capture = new FoliaRegionSnapshotCapture(
            context.server(),
            context.scheduler(),
            observationRegistry,
            context.clock(),
            configuration.folia().observationTtl(),
            configuration.folia().observationSources(),
            windows
        );
        collector = new PeriodicSnapshotCollector<>(
            "folia",
            true,
            context.scheduler(),
            configuration.collection().foliaInterval(),
            configuration.collection().timeout(),
            capture,
            metrics.repository(),
            context.clock(),
            (ignored, failure) -> context.failureListener().accept(
                "folia",
                sanitizeFailure(failure)
            )
        );
        metrics.register();
    }

    @Override
    public void start() throws Exception {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        observationRegistry.start();
        try {
            collector.start();
        } catch (Exception failure) {
            observationRegistry.stop();
            started.set(false);
            throw failure;
        }
    }

    @Override
    public void stop() throws Exception {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        try {
            collector.stop();
        } finally {
            observationRegistry.stop();
        }
    }

    private static List<String> orderedStatistics(List<String> configured) {
        return List.of("min", "p05", "p50", "p95", "max", "average")
            .stream()
            .filter(configured::contains)
            .toList();
    }

    static Throwable sanitizeFailure(Throwable failure) {
        IllegalStateException sanitized = new IllegalStateException(
            "A Folia region observation run failed; anchor details were suppressed."
        );
        sanitized.setStackTrace(failure.getStackTrace());
        return sanitized;
    }
}
