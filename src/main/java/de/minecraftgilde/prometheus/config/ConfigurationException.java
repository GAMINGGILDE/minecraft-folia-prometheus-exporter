package de.minecraftgilde.prometheus.config;

/** Signals an invalid or unsupported exporter configuration value. */
public final class ConfigurationException extends IllegalArgumentException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
