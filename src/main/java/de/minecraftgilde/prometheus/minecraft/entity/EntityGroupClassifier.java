package de.minecraftgilde.prometheus.minecraft.entity;

import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.WaterMob;

/** Classifies public Bukkit entity types through stable public interfaces. */
public final class EntityGroupClassifier {

    public EntityGroup classify(EntityType type) {
        if (type == null || type == EntityType.UNKNOWN) {
            return EntityGroup.OTHER;
        }
        Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass == null) {
            return EntityGroup.OTHER;
        }

        // The order is part of the public entity metric definition.
        if (AbstractVillager.class.isAssignableFrom(entityClass)) {
            return EntityGroup.VILLAGER;
        }
        if (Item.class.isAssignableFrom(entityClass)) {
            return EntityGroup.ITEM;
        }
        if (Projectile.class.isAssignableFrom(entityClass)) {
            return EntityGroup.PROJECTILE;
        }
        if (Vehicle.class.isAssignableFrom(entityClass)) {
            return EntityGroup.VEHICLE;
        }
        if (Display.class.isAssignableFrom(entityClass)) {
            return EntityGroup.DISPLAY;
        }
        if (Enemy.class.isAssignableFrom(entityClass)) {
            return EntityGroup.MONSTER;
        }
        if (WaterMob.class.isAssignableFrom(entityClass)) {
            return EntityGroup.WATER;
        }
        if (Ambient.class.isAssignableFrom(entityClass)) {
            return EntityGroup.AMBIENT;
        }
        if (Animals.class.isAssignableFrom(entityClass)) {
            return EntityGroup.ANIMAL;
        }
        return EntityGroup.OTHER;
    }

    public boolean excluded(EntityType type) {
        return type == null || type == EntityType.PLAYER;
    }
}
