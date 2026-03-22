package com.arcadia.pets.client;

import com.arcadia.pets.ArcadiaPets;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.network.C2SAuraTick;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Client-side detection for aura skills (Wither Aura, Soul Drain).
 * Scans nearby entities on the client every 40 ticks; if hostiles are found,
 * sends C2SAuraTick so the server applies the aura without scanning itself.
 */
@EventBusSubscriber(modid = ArcadiaPets.MOD_ID, value = Dist.CLIENT)
public final class PetAuraClientHandler {

    private static final int AURA_INTERVAL = 40;
    private static final float AURA_MAX_RANGE = 7f; // max range across all aura skills

    private PetAuraClientHandler() {}

    /**
     * Returns true if the player's inventory contains a pet item with at least one
     * aura skill at level > 0. Returns true (allow packet) if no pet item is found,
     * since the pet may be stored in the server-side collection.
     */
    private static boolean inventoryHasAuraSkill(Player player) {
        boolean foundPet = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            PetData data = PetData.fromStack(stack);
            if (data == null) continue;
            foundPet = true;
            for (var si : data.skills()) {
                if (si.level() > 0 && si.skill().isAuraTick()) return true;
            }
        }
        // Pet not found in inventory — may be in collection; allow packet (server validates)
        return !foundPet;
    }

    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) return;
        if (player != Minecraft.getInstance().player) return;
        if (!ClientPetState.isActive()) return;
        if (player.tickCount % AURA_INTERVAL != 0) return;

        // Skip entity scan entirely if no pet in inventory has an active aura skill.
        // If the pet is stored in the collection (not hotbar/inventory), allow the scan
        // and let the server validate — this is the safe fallback.
        if (!inventoryHasAuraSkill(player)) return;

        List<LivingEntity> nearby = player.level().getEntitiesOfClass(
                LivingEntity.class, player.getBoundingBox().inflate(AURA_MAX_RANGE));

        boolean hasTarget = false;
        for (LivingEntity e : nearby) {
            if (e != player && e instanceof Enemy) { hasTarget = true; break; }
        }

        if (hasTarget) {
            PacketDistributor.sendToServer(new C2SAuraTick());
        }
    }
}
