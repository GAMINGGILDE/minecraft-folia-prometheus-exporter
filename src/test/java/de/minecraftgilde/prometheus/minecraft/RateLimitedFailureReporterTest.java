package de.minecraftgilde.prometheus.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class RateLimitedFailureReporterTest {

    @Test
    void limitsEachCollectorIndependentlyAndHonorsTheLoggingSwitch() {
        AtomicInteger warnings = new AtomicInteger();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new CountingHandler(warnings));
        MutableClock clock = new MutableClock(Instant.EPOCH);
        RateLimitedFailureReporter reporter = new RateLimitedFailureReporter(
            logger,
            true,
            clock,
            Duration.ofMinutes(5)
        );

        reporter.accept("worlds", new IllegalStateException("first"));
        reporter.accept("worlds", new IllegalStateException("suppressed"));
        reporter.accept("chunks", new IllegalStateException("independent"));
        assertEquals(2, warnings.get());

        clock.advance(Duration.ofMinutes(5));
        reporter.accept("worlds", new IllegalStateException("allowed again"));
        assertEquals(3, warnings.get());

        new RateLimitedFailureReporter(logger, false, clock)
            .accept("server", new IllegalStateException("disabled"));
        assertEquals(3, warnings.get());
    }

    private static final class CountingHandler extends Handler {

        private final AtomicInteger warnings;

        private CountingHandler(AtomicInteger warnings) {
            this.warnings = warnings;
        }

        @Override
        public void publish(LogRecord record) {
            warnings.incrementAndGet();
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
