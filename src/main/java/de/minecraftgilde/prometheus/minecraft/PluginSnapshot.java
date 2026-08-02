package de.minecraftgilde.prometheus.minecraft;

import java.util.Objects;

/** Immutable, identity-free plugin information used only for optional labels. */
public record PluginSnapshot(String name, String version, boolean enabled) {

    public PluginSnapshot {
        name = Objects.requireNonNull(name, "name");
        version = Objects.requireNonNull(version, "version");
    }
}
