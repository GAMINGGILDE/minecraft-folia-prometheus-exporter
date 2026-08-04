package de.minecraftgilde.prometheus.minecraft.entity;

import java.util.Objects;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/** Registers the entity listener through public Bukkit lifecycle APIs. */
final class BukkitEntityEventRegistration implements EntityEventRegistration {

    private final Plugin plugin;

    BukkitEntityEventRegistration(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void register(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void unregister(Listener listener) {
        HandlerList.unregisterAll(listener);
    }
}
