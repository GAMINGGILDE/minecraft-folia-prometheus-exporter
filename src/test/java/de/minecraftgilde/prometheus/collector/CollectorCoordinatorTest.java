package de.minecraftgilde.prometheus.collector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollectorCoordinatorTest {

    @Test
    void rejectsDuplicateNames() {
        CollectorCoordinator coordinator = coordinator(new ArrayList<>());
        coordinator.register(new RecordingCollector("same", new ArrayList<>(), false));

        assertThrows(
            IllegalArgumentException.class,
            () -> coordinator.register(
                new RecordingCollector("same", new ArrayList<>(), false)
            )
        );
    }

    @Test
    void startsDeterministicallyAndStopsInReverseOrder() {
        List<String> events = new ArrayList<>();
        CollectorCoordinator coordinator = coordinator(new ArrayList<>());
        coordinator.register(new RecordingCollector("first", events, false));
        coordinator.register(new RecordingCollector("second", events, false));
        coordinator.register(new RecordingCollector("third", events, false));

        coordinator.startAll();
        coordinator.startAll();
        coordinator.stopAll();
        coordinator.stopAll();

        assertEquals(
            List.of(
                "start:first",
                "start:second",
                "start:third",
                "stop:third",
                "stop:second",
                "stop:first"
            ),
            events
        );
    }

    @Test
    void isolatesCollectorFailures() {
        List<String> events = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        CollectorCoordinator coordinator = coordinator(failures);
        coordinator.register(new RecordingCollector("bad", events, true));
        coordinator.register(new RecordingCollector("good", events, false));

        coordinator.startAll();

        assertEquals(CollectorState.FAILED, coordinator.states().get("bad"));
        assertEquals(CollectorState.RUNNING, coordinator.states().get("good"));
        assertEquals(List.of("bad"), failures);
        assertEquals(
            List.of("start:bad", "stop:bad", "start:good"),
            events
        );
    }

    @Test
    void exposesAnImmutableStateView() {
        CollectorCoordinator coordinator = coordinator(new ArrayList<>());
        coordinator.register(new RecordingCollector("one", new ArrayList<>(), false));

        assertThrows(
            UnsupportedOperationException.class,
            () -> coordinator.states().put("two", CollectorState.RUNNING)
        );
    }

    private static CollectorCoordinator coordinator(List<String> failures) {
        return new CollectorCoordinator(
            (name, state) -> {},
            (name, failure) -> failures.add(name)
        );
    }

    private static final class RecordingCollector extends AbstractCollector {

        private final List<String> events;
        private final boolean failOnStart;

        private RecordingCollector(
            String name,
            List<String> events,
            boolean failOnStart
        ) {
            super(name, true);
            this.events = events;
            this.failOnStart = failOnStart;
        }

        @Override
        protected void startCollector() {
            events.add("start:" + name());
            if (failOnStart) {
                throw new IllegalStateException("expected failure");
            }
        }

        @Override
        protected void stopCollector() {
            events.add("stop:" + name());
        }
    }
}
