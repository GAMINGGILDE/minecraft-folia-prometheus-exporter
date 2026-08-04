package de.minecraftgilde.prometheus.minecraft.entity;

import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.entity.EntityType;

/** Bounded normalization of public EntityType names for the optional label. */
public final class EntityTypeLabel {

    public static final String UNKNOWN = "unknown";

    private static final Pattern KEY = Pattern.compile(
        "[a-z0-9_.-]+:[a-z0-9_./-]+"
    );

    private EntityTypeLabel() {}

    public static String normalize(EntityType type) {
        if (type == null || type == EntityType.UNKNOWN) {
            return UNKNOWN;
        }
        try {
            String value = type.getKey().toString().toLowerCase(Locale.ROOT);
            return KEY.matcher(value).matches() ? value : UNKNOWN;
        } catch (RuntimeException ignored) {
            return UNKNOWN;
        }
    }
}
