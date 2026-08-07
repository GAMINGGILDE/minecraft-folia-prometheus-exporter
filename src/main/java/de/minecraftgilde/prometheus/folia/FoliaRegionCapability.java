package de.minecraftgilde.prometheus.folia;

import java.lang.reflect.Method;
import java.util.Objects;
import org.bukkit.World;

/** Detects only the public Folia region-TPS method required by the provider. */
public final class FoliaRegionCapability {

    private FoliaRegionCapability() {}

    public static boolean isAvailable(Class<?> serverApiType) {
        Objects.requireNonNull(serverApiType, "serverApiType");
        try {
            Method method = serverApiType.getMethod(
                "getRegionTPS",
                World.class,
                int.class,
                int.class
            );
            return method.getReturnType() == double[].class;
        } catch (NoSuchMethodException | SecurityException unavailable) {
            return false;
        }
    }
}
