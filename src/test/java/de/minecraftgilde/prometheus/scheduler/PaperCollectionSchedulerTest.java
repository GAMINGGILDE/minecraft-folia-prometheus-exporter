package de.minecraftgilde.prometheus.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PaperCollectionSchedulerTest {

    @Test
    void roundsPositiveDurationsUpToWholeServerTicks() {
        assertEquals(1L, PaperCollectionScheduler.toTicks(Duration.ofMillis(1)));
        assertEquals(1L, PaperCollectionScheduler.toTicks(Duration.ofMillis(50)));
        assertEquals(2L, PaperCollectionScheduler.toTicks(Duration.ofMillis(51)));
    }

    @Test
    void rejectsDurationsBelowOneMillisecond() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PaperCollectionScheduler.toTicks(Duration.ZERO)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PaperCollectionScheduler.toTicks(Duration.ofNanos(1))
        );
    }
}
