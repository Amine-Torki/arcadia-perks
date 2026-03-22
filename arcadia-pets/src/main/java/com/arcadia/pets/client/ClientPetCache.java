package com.arcadia.pets.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches a single, persistent instance of a LivingEntity for each pet type on the client.
 * This prevents the massive FPS drop of creating a new Entity object every single frame
 * when rendering pets in the inventory or on the ground.
 */
public final class ClientPetCache {

    private static final Map<String, LivingEntity> ENTITY_CACHE = new ConcurrentHashMap<>();

    private ClientPetCache() {}

    /**
     * Returns a customized, cached entity instance for the given mob type. 
     * If the entity has not been created yet, it will be instantiated.
     */
    public static LivingEntity getEntity(String mobType) {
        return ENTITY_CACHE.computeIfAbsent(mobType, typeStr -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;

            ResourceLocation rl = ResourceLocation.parse(typeStr);
            Optional<EntityType<?>> typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(rl);
            if (typeOpt.isEmpty()) return null;

            if (typeOpt.get().create(mc.level) instanceof LivingEntity le) {
                // Strip all AI and tick behaviors to ensure it's purely a visual dummy
                if (le instanceof net.minecraft.world.entity.Mob m) {
                    m.setNoAi(true);
                }
                return le;
            }
            return null;
        });
    }

    /** Clears the cache when disconnecting or when memory gets tight. */
    public static void clear() {
        ENTITY_CACHE.clear();
    }
}
