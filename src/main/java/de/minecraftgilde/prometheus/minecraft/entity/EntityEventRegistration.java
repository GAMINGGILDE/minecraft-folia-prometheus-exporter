package de.minecraftgilde.prometheus.minecraft.entity;

import org.bukkit.event.Listener;

interface EntityEventRegistration {

    void register(Listener listener);

    void unregister(Listener listener);
}
