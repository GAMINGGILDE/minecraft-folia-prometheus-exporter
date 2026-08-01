package de.minecraftgilde.prometheus.collector;

/** Signals that an optional collector cannot run on the current public API. */
public final class UnsupportedCollectorException extends Exception {

    public UnsupportedCollectorException(String message) {
        super(message);
    }
}
