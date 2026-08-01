package de.minecraftgilde.prometheus.config;

/** Provides configuration values by their dotted YAML path. */
@FunctionalInterface
public interface ConfigurationSource {

    Object get(String path);
}
