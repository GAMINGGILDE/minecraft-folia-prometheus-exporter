package de.minecraftgilde.prometheus.collector;

import java.util.List;

/** Completes exactly one snapshot capture. Late duplicate calls are ignored. */
public interface SnapshotCompletion<T> {

    void success(List<T> values);

    void failure(Throwable failure);
}
