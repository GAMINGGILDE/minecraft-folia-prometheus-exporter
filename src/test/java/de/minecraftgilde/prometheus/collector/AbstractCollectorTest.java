package de.minecraftgilde.prometheus.collector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AbstractCollectorTest {

    @Test
    void transitionsThroughStartAndIdempotentStop() throws Exception {
        TestCollector collector = new TestCollector("example", true);

        assertEquals(CollectorState.STOPPED, collector.state());
        collector.start();
        collector.start();
        assertEquals(CollectorState.RUNNING, collector.state());
        assertEquals(1, collector.initializations.get());
        assertEquals(1, collector.starts.get());

        collector.stop();
        collector.stop();
        assertEquals(CollectorState.STOPPED, collector.state());
        assertEquals(1, collector.stops.get());
        assertFalse(collector.failureCause().isPresent());
    }

    @Test
    void preventsParallelStarts() throws Exception {
        CountDownLatch enteredStart = new CountDownLatch(1);
        CountDownLatch releaseStart = new CountDownLatch(1);
        TestCollector collector = new TestCollector("parallel", true) {
            @Override
            protected void startCollector() throws Exception {
                starts.incrementAndGet();
                enteredStart.countDown();
                assertTrue(releaseStart.await(5, TimeUnit.SECONDS));
            }
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> startUnchecked(collector));
            assertTrue(enteredStart.await(5, TimeUnit.SECONDS));
            assertEquals(CollectorState.STARTING, collector.state());
            Future<?> second = executor.submit(() -> startUnchecked(collector));
            releaseStart.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertEquals(CollectorState.RUNNING, collector.state());
        assertEquals(1, collector.starts.get());
    }

    @Test
    void recordsFailureAndCleansUpPartialStart() {
        Exception failure = new Exception("expected");
        TestCollector collector = new TestCollector("failing", true) {
            @Override
            protected void startCollector() throws Exception {
                starts.incrementAndGet();
                throw failure;
            }
        };

        assertEquals(failure, assertThrows(Exception.class, collector::start));
        assertEquals(CollectorState.FAILED, collector.state());
        assertEquals(failure, collector.failureCause().orElseThrow());
        assertEquals(1, collector.stops.get());
    }

    @Test
    void supportsDisabledAndUnsupportedStates() throws Exception {
        TestCollector disabled = new TestCollector("disabled", false);
        disabled.start();
        disabled.stop();
        assertEquals(CollectorState.DISABLED, disabled.state());
        assertEquals(0, disabled.starts.get());

        TestCollector unsupported = new TestCollector("unsupported", true) {
            @Override
            protected void startCollector() throws Exception {
                starts.incrementAndGet();
                throw new UnsupportedCollectorException("not available");
            }
        };
        unsupported.start();
        assertEquals(CollectorState.UNSUPPORTED, unsupported.state());
        assertFalse(unsupported.failureCause().isPresent());
        assertEquals(1, unsupported.stops.get());
    }

    @Test
    void rejectsUnstableCollectorNames() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new TestCollector("Player Name", true)
        );
    }

    private static void startUnchecked(ManagedCollector collector) {
        try {
            collector.start();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static class TestCollector extends AbstractCollector {

        protected final AtomicInteger initializations = new AtomicInteger();
        protected final AtomicInteger starts = new AtomicInteger();
        protected final AtomicInteger stops = new AtomicInteger();

        TestCollector(String name, boolean enabled) {
            super(name, enabled);
        }

        @Override
        protected void initialize() {
            initializations.incrementAndGet();
        }

        @Override
        protected void startCollector() throws Exception {
            starts.incrementAndGet();
        }

        @Override
        protected void stopCollector() {
            stops.incrementAndGet();
        }
    }
}
