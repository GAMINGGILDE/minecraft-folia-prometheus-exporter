package de.minecraftgilde.prometheus.minecraft.event;

import java.util.Objects;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/** Registers and removes one listener through the public Bukkit API. */
final class BukkitEventRegistration implements EventRegistration {

    private final Plugin plugin;

    BukkitEventRegistration(Plugin plugin) {
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
