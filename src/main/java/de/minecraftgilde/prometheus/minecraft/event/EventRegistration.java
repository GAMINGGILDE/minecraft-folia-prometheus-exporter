package de.minecraftgilde.prometheus.minecraft.event;

import org.bukkit.event.Listener;

interface EventRegistration {

    void register(Listener listener);

    void unregister(Listener listener);
}
