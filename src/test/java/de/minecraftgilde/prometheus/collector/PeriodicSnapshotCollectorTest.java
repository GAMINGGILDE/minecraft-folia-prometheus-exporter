package de.minecraftgilde.prometheus.collector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.minecraftgilde.prometheus.scheduler.ManualCollectionScheduler;
import de.minecraftgilde.prometheus.snapshot.SnapshotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PeriodicSnapshotCollectorTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-02T10:00:00Z"),
        ZoneOffset.UTC
    );

    @Test
    void startAndStopAreIdempotentAndDisabledCollectorsNeverSchedule()
        throws Exception {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        PeriodicSnapshotCollector<Integer> enabled = collector(
            "enabled",
            true,
            scheduler,
            completion -> completion.success(List.of(1)),
            new SnapshotRepository<>(),
            new ArrayList<>()
        );
        PeriodicSnapshotCollector<Integer> disabled = collector(
            "disabled-periodic",
            false,
            scheduler,
            completion -> completion.success(List.of(2)),
            new SnapshotRepository<>(),
            new ArrayList<>()
        );

        enabled.start();
        enabled.start();
        disabled.start();
        assertEquals(1, scheduler.activeGlobalTasks());

        enabled.stop();
        enabled.stop();
        disabled.stop();
        assertEquals(0, scheduler.activeGlobalTasks());
        assertEquals(CollectorState.DISABLED, disabled.state());
    }

    @Test
    void preventsOverlapAndPublishesOnlyCompleteValues() throws Exception {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        List<SnapshotCompletion<Integer>> completions = new ArrayList<>();
        SnapshotRepository<Integer> repository = new SnapshotRepository<>();
        PeriodicSnapshotCollector<Integer> collector = collector(
            "overlap",
            true,
            scheduler,
            completions::add,
            repository,
            new ArrayList<>()
        );
        collector.start();

        scheduler.runGlobal();
        scheduler.runGlobal();
        assertEquals(1, completions.size());
        assertFalse(repository.hasSnapshot());

        completions.getFirst().success(List.of(3, 3));
        assertEquals(List.of(3, 3), repository.current().orElseThrow().values());
    }

    @Test
    void timeoutRejectsLateGenerationsAndKeepsLastSuccessfulSnapshot()
        throws Exception {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        List<SnapshotCompletion<Integer>> completions = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        SnapshotRepository<Integer> repository = new SnapshotRepository<>();
        repository.publish(
            new de.minecraftgilde.prometheus.snapshot.ImmutableSnapshot<>(
                Instant.EPOCH,
                List.of(7)
            )
        );
        PeriodicSnapshotCollector<Integer> collector = collector(
            "timeout-test",
            true,
            scheduler,
            completions::add,
            repository,
            failures
        );
        collector.start();

        scheduler.runGlobal();
        AtomicInteger inactiveCallbacks = new AtomicInteger();
        completions.getFirst().whenInactive(inactiveCallbacks::incrementAndGet);
        assertTrue(completions.getFirst().isActive());
        scheduler.runDelayed();
        assertFalse(completions.getFirst().isActive());
        assertEquals(1, inactiveCallbacks.get());
        assertEquals(List.of(7), repository.current().orElseThrow().values());
        assertEquals(1, failures.size());

        scheduler.runGlobal();
        assertEquals(2, completions.size());
        completions.get(1).success(List.of(9));
        completions.getFirst().success(List.of(8));

        assertEquals(List.of(9), repository.current().orElseThrow().values());
    }

    @Test
    void timeoutDoesNotEvaluateTransactionalSuccessValues() throws Exception {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        List<SnapshotCompletion<Integer>> completions = new ArrayList<>();
        SnapshotRepository<Integer> repository = new SnapshotRepository<>();
        PeriodicSnapshotCollector<Integer> collector = collector(
            "transactional-timeout",
            true,
            scheduler,
            completions::add,
            repository,
            new ArrayList<>()
        );
        collector.start();
        scheduler.runGlobal();
        scheduler.runDelayed();
        AtomicInteger evaluations = new AtomicInteger();

        boolean accepted = completions.getFirst().successIfActive(() -> {
            evaluations.incrementAndGet();
            return List.of(1);
        });

        assertFalse(accepted);
        assertEquals(0, evaluations.get());
        assertFalse(repository.hasSnapshot());
    }

    @Test
    void neverPublishesAfterStop() throws Exception {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        List<SnapshotCompletion<Integer>> completions = new ArrayList<>();
        SnapshotRepository<Integer> repository = new SnapshotRepository<>();
        PeriodicSnapshotCollector<Integer> collector = collector(
            "stopping",
            true,
            scheduler,
            completions::add,
            repository,
            new ArrayList<>()
        );
        collector.start();
        scheduler.runGlobal();

        collector.stop();
        AtomicInteger inactiveCallbacks = new AtomicInteger();
        completions.getFirst().whenInactive(inactiveCallbacks::incrementAndGet);
        collector.stop();
        completions.getFirst().success(List.of(10));

        assertFalse(repository.hasSnapshot());
        assertFalse(completions.getFirst().isActive());
        assertEquals(1, inactiveCallbacks.get());
        assertEquals(CollectorState.STOPPED, collector.state());
    }

    @Test
    void runtimeFailureRetainsTheSnapshotAndKeepsTheCollectorRunning()
        throws Exception {
        ManualCollectionScheduler scheduler = new ManualCollectionScheduler();
        SnapshotRepository<Integer> repository = new SnapshotRepository<>();
        repository.publish(
            new de.minecraftgilde.prometheus.snapshot.ImmutableSnapshot<>(
                Instant.EPOCH,
                List.of(7)
            )
        );
        List<Throwable> failures = new ArrayList<>();
        PeriodicSnapshotCollector<Integer> collector = collector(
            "runtime-failure",
            true,
            scheduler,
            completion -> completion.failure(
                new IllegalStateException("temporary")
            ),
            repository,
            failures
        );

        collector.start();
        scheduler.runGlobal();

        assertEquals(CollectorState.RUNNING, collector.state());
        assertEquals(List.of(7), repository.current().orElseThrow().values());
        assertEquals(1, failures.size());
    }

    private static PeriodicSnapshotCollector<Integer> collector(
        String name,
        boolean enabled,
        ManualCollectionScheduler scheduler,
        SnapshotCapture<Integer> capture,
        SnapshotRepository<Integer> repository,
        List<Throwable> failures
    ) {
        return new PeriodicSnapshotCollector<>(
            name,
            enabled,
            scheduler,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            capture,
            repository,
            CLOCK,
            (ignored, failure) -> failures.add(failure)
        );
    }
}
