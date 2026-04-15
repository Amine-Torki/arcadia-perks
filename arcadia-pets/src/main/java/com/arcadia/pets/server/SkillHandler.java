package com.arcadia.pets.server;

import com.arcadia.pets.ArcadiaPets;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetStat;
import com.arcadia.pets.skill.PetSkill;
import com.arcadia.pets.skill.SkillInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the periodic execution and event triggers for pet skills.
 */
@EventBusSubscriber(modid = ArcadiaPets.MOD_ID)
public class SkillHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        // Skip every other tick entirely for skill processing (50% reduction)
        if (player.tickCount % 2 != 0) return;

        PetData active = PetManager.getActivePetData(player.getUUID());
        if (active != null) executeSkills(player, active, Trigger.TICK, null);

        PetData pocket = PetManager.getPocketPetData(player.getUUID());
        if (pocket != null) executeSkills(player, pocket, Trigger.TICK, null);
    }

    @SubscribeEvent
    public static void onOwnerHurt(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PetData active = PetManager.getActivePetData(player.getUUID());
            if (active != null) executeSkills(player, active, Trigger.HURT, event);
            
            PetData pocket = PetManager.getPocketPetData(player.getUUID());
            if (pocket != null) executeSkills(player, pocket, Trigger.HURT, event);
        }
    }

    @SubscribeEvent
    public static void onOwnerAttack(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            PetData active = PetManager.getActivePetData(player.getUUID());
            if (active != null) executeSkills(player, active, Trigger.ATTACK, event);
            
            PetData pocket = PetManager.getPocketPetData(player.getUUID());
            if (pocket != null) executeSkills(player, pocket, Trigger.ATTACK, event);
        }
    }

    @SubscribeEvent
    public static void onMobKilled(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            PetData active = PetManager.getActivePetData(player.getUUID());
            if (active != null) executeSkills(player, active, Trigger.KILL, event);
            
            PetData pocket = PetManager.getPocketPetData(player.getUUID());
            if (pocket != null) executeSkills(player, pocket, Trigger.KILL, event);
        }
    }

    // ── Temporary entity TTL ─────────────────────────────────────────────────
    // Instead of EntityTickEvent (fires for every entity in the world every tick),
    // we maintain a targeted map of UUID → remaining ticks.
    // registerTemp() is called by skill code when spawning a temp entity.

    private static final Map<UUID, Integer> TEMP_ENTITIES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Registers an entity for auto-discard after {@code ticks} server ticks. */
    public static void registerTemp(net.minecraft.world.entity.Entity entity, int ticks) {
        TEMP_ENTITIES.put(entity.getUUID(), ticks);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TEMP_ENTITIES.isEmpty()) return;
        var iter = TEMP_ENTITIES.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                UUID id = entry.getKey();
                for (var level : event.getServer().getAllLevels()) {
                    net.minecraft.world.entity.Entity e = level.getEntity(id);
                    if (e != null) { e.discard(); break; }
                }
                iter.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            PetData active = PetManager.getActivePetData(player.getUUID());
            if (active != null) executeSkills(player, active, Trigger.BLOCK_BREAK, event);
            
            PetData pocket = PetManager.getPocketPetData(player.getUUID());
            if (pocket != null) executeSkills(player, pocket, Trigger.BLOCK_BREAK, event);
        }
    }

    public static void triggerSummon(ServerPlayer player) {
        PetData active = PetManager.getActivePetData(player.getUUID());
        if (active != null) executeSkills(player, active, Trigger.SUMMON, null);
        PetData pocket = PetManager.getPocketPetData(player.getUUID());
        if (pocket != null) executeSkills(player, pocket, Trigger.SUMMON, null);
    }

    public static void triggerRecall(ServerPlayer player) {
        PetData active = PetManager.getActivePetData(player.getUUID());
        if (active != null) executeSkills(player, active, Trigger.RECALL, null);
        PetData pocket = PetManager.getPocketPetData(player.getUUID());
        if (pocket != null) executeSkills(player, pocket, Trigger.RECALL, null);
    }

    /** Strips all known pet attribute modifiers from the player. Call on recall, login, and integrity check. */
    public static void removeAllPetAttributes(Player player) {
        removeAttr(player, com.arcadia.pets.skill.PetSkills.ATTR_FOX_SPEED,
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        removeAttr(player, com.arcadia.pets.skill.PetSkills.ATTR_FROG_JUMP,
                net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
        removeAttr(player, com.arcadia.pets.skill.PetSkills.ATTR_RABBIT_LUCK,
                net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
    }

    private static void removeAttr(Player player, net.minecraft.resources.ResourceLocation id,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        var attr = player.getAttribute(attribute);
        if (attr != null) attr.removeModifier(id);
    }

    public static void triggerAuraTick(ServerPlayer player) {
        PetData active = PetManager.getActivePetData(player.getUUID());
        if (active != null && active.hunger() > 0) {
            for (SkillInstance si : active.skills()) {
                if (si.level() > 0 && !PetManager.isSkillDisabled(player.getUUID(), si.skill().getId()))
                    si.skill().onAuraTick(player, active, si.level(), applyGeneBonus(si.effectiveness(), si.skill(), active));
            }
        }
        PetData pocket = PetManager.getPocketPetData(player.getUUID());
        if (pocket != null && pocket.hunger() > 0) {
            for (SkillInstance si : pocket.skills()) {
                if (si.level() > 0 && !PetManager.isSkillDisabled(player.getUUID(), si.skill().getId()))
                    si.skill().onAuraTick(player, pocket, si.level(), applyGeneBonus(si.effectiveness(), si.skill(), pocket));
            }
        }
    }

    public static void triggerAftershock(ServerPlayer player, LivingEntity target) {
        PetData active = PetManager.getActivePetData(player.getUUID());
        if (active != null) executeSkills(player, active, Trigger.AFTERSHOCK, target);
        
        PetData pocket = PetManager.getPocketPetData(player.getUUID());
        if (pocket != null) executeSkills(player, pocket, Trigger.AFTERSHOCK, target);
    }

    private static void executeSkills(Player player, PetData data, Trigger trigger, Object event) {
        // Starving pets have all skills disabled
        if (data.hunger() <= 0) return;
        boolean anyActive = false;
        boolean isPerTickThrottle = trigger == Trigger.TICK && player.tickCount % 4 != 0;
        boolean isSlowThrottle   = trigger == Trigger.TICK && player.tickCount % 20 != 0;
        for (SkillInstance instance : data.skills()) {
            if (instance.level() == 0) continue;
            if (PetManager.isSkillDisabled(player.getUUID(), instance.skill().getId())) continue;
            // Throttle per-tick skills to every 4 ticks (5x/s), others to every 20 ticks
            if (instance.skill().isPerTick() && isPerTickThrottle) continue;
            if (!instance.skill().isPerTick() && isSlowThrottle) continue;
            anyActive = true;
            // Gene bonus: +4% effectiveness per star of the skill's linked stat
            float eff = applyGeneBonus(instance.effectiveness(), instance.skill(), data);
            switch (trigger) {
                case TICK -> instance.skill().onTick(player, data, instance.level(), eff);
                case HURT -> instance.skill().onOwnerHurt((LivingDamageEvent.Pre) event, player, data, instance.level(), eff);
                case ATTACK -> instance.skill().onOwnerAttack((LivingDamageEvent.Pre) event, player, data, instance.level(), eff);
                case KILL -> instance.skill().onOwnerKill((net.neoforged.neoforge.event.entity.living.LivingDropsEvent) event, player, data, instance.level(), eff);
                case BLOCK_BREAK -> instance.skill().onBlockBreak((BlockEvent.BreakEvent) event, player, data, instance.level(), eff);
                case AFTERSHOCK -> instance.skill().onAftershock(player, data, instance.level(), eff, (LivingEntity) event);
                case SUMMON -> instance.skill().onSummon(player, data, instance.level(), eff);
                case RECALL -> instance.skill().onRecall(player, data, instance.level(), eff);
            }
        }
        // Active skill use costs 1 hunger (aftershock is rate-limited at 5s so this won't spike)
        if (anyActive && trigger == Trigger.AFTERSHOCK && player instanceof ServerPlayer sp) {
            PetManager.drainHunger(sp, data.petId(), 2);
        }
    }

    /** Applies gene stat bonus to effectiveness: +4% per star of the skill's linked stat. */
    private static float applyGeneBonus(float baseEffectiveness, PetSkill skill, PetData data) {
        PetStat linked = skill.getSkillType().getLinkedStat();
        int stars = data.stats().getOrDefault(linked, 0);
        return baseEffectiveness * (1.0f + stars * 0.04f);
    }

    private enum Trigger {
        TICK, HURT, ATTACK, KILL, BLOCK_BREAK, AFTERSHOCK, SUMMON, RECALL
    }
}
