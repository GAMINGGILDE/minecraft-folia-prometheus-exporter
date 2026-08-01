package de.minecraftgilde.prometheus.snapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Lock-free publication and reads of the latest complete immutable snapshot. */
public final class SnapshotRepository<T> {

    private final AtomicReference<ImmutableSnapshot<T>> current =
        new AtomicReference<>();

    public Optional<ImmutableSnapshot<T>> publish(ImmutableSnapshot<T> snapshot) {
        return Optional.ofNullable(
            current.getAndSet(Objects.requireNonNull(snapshot, "snapshot"))
        );
    }

    public Optional<ImmutableSnapshot<T>> current() {
        return Optional.ofNullable(current.get());
    }

    public boolean hasSnapshot() {
        return current.get() != null;
    }

    public Optional<Instant> capturedAt() {
        return current().map(ImmutableSnapshot::capturedAt);
    }

    public Optional<Duration> age(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return capturedAt().map(capturedAt -> {
            Duration age = Duration.between(capturedAt, clock.instant());
            return age.isNegative() ? Duration.ZERO : age;
        });
    }

    public Optional<ImmutableSnapshot<T>> clear() {
        return Optional.ofNullable(current.getAndSet(null));
    }

    public boolean remove(ImmutableSnapshot<T> expected) {
        return current.compareAndSet(Objects.requireNonNull(expected, "expected"), null);
    }
}
