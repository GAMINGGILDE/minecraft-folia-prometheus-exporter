package de.minecraftgilde.prometheus.minecraft;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Prevents recurring collection failures from flooding the server log. */
public final class RateLimitedFailureReporter
    implements BiConsumer<String, Throwable> {

    private static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(5);

    private final Logger logger;
    private final boolean enabled;
    private final Clock clock;
    private final Duration interval;
    private final ConcurrentHashMap<String, Instant> lastLogged =
        new ConcurrentHashMap<>();

    public RateLimitedFailureReporter(Logger logger, boolean enabled, Clock clock) {
        this(logger, enabled, clock, DEFAULT_INTERVAL);
    }

    RateLimitedFailureReporter(
        Logger logger,
        boolean enabled,
        Clock clock,
        Duration interval
    ) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.enabled = enabled;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.interval = Objects.requireNonNull(interval, "interval");
    }

    @Override
    public void accept(String collector, Throwable failure) {
        Objects.requireNonNull(collector, "collector");
        Objects.requireNonNull(failure, "failure");
        if (!enabled) {
            return;
        }
        Instant now = clock.instant();
        AtomicDecision decision = new AtomicDecision();
        lastLogged.compute(collector, (ignored, previous) -> {
            if (previous == null || !now.isBefore(previous.plus(interval))) {
                decision.log = true;
                return now;
            }
            return previous;
        });
        if (decision.log) {
            logger.log(
                Level.WARNING,
                "Collection source '" + collector + "' reported a failure.",
                failure
            );
        }
    }

    private static final class AtomicDecision {

        private boolean log;
    }
}
