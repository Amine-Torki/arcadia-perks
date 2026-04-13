package com.arcadia.prestige.quest;

import com.arcadia.lib.event.QuestProgressEvent;
import com.arcadia.prestige.ArcadiaDashboard;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Server-side listener that routes relevant game events into {@link QuestManager}.
 *
 * <p>Events that originate in other modules (PET_SUMMON, AH_SELL, …) arrive via
 * {@link QuestProgressEvent} fired by those modules on the NeoForge bus.</p>
 *
 * <p>Events that are always available in this module (mob kills, block breaks) are
 * handled directly here to avoid unnecessary cross-module traffic.</p>
 */
@EventBusSubscriber(modid = ArcadiaDashboard.MOD_ID)
public final class QuestEventHandler {

    private QuestEventHandler() {}

    // ── Cross-module progress events ──────────────────────────────────────────

    @SubscribeEvent
    public static void onQuestProgress(QuestProgressEvent event) {
        QuestManager.trackProgress(
                event.getPlayerUuid(),
                event.getTypeId(),
                event.getContext(),
                event.getAmount());
    }

    // ── Mob kills ─────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onMobKill(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() instanceof ServerPlayer) return; // skip player kills

        String mobType = ResourceLocation.defaultNamespace(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getKey(event.getEntity().getType()).toString()).toString();

        // Track generic kill-any progress
        QuestManager.trackProgress(player.getUUID(), QuestType.KILL_ANY.name(), "", 1);
        // Track specific mob progress
        QuestManager.trackProgress(player.getUUID(), QuestType.KILL_MOB.name(), mobType, 1);
    }

    // ── Block breaks ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        String blockType = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(event.getState().getBlock()).toString();

        QuestManager.trackProgress(player.getUUID(), QuestType.BLOCK_BREAK.name(), blockType, 1);
    }
}
