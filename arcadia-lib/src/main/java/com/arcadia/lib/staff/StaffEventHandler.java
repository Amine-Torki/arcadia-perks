package com.arcadia.lib.staff;

import com.arcadia.lib.ArcadiaLib;
import com.arcadia.lib.ArcadiaMessages;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Enforces mutes and handles staff chat toggle interception.
 */
@EventBusSubscriber(modid = ArcadiaLib.MOD_ID)
public final class StaffEventHandler {

    private StaffEventHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();

        // Mute enforcement
        if (StaffActions.isMuted(player.getUUID())) {
            long remaining = StaffActions.getMuteRemaining(player.getUUID());
            String reason = StaffActions.getMuteReason(player.getUUID());
            player.sendSystemMessage(ArcadiaMessages.error(
                    net.minecraft.network.chat.Component.translatable("arcadia_lib.staff.muted_feedback",
                            com.arcadia.lib.text.TextFormatter.formatMs(remaining),
                            reason != null ? reason : "").getString()));
            event.setCanceled(true);
            return;
        }

        // Staff chat toggle: redirect message to staff channel
        if (StaffChatService.isToggled(player.getUUID()) && StaffService.isStaff(player)) {
            StaffChatService.broadcast(player, event.getRawText());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        StaffChatService.onDisconnect(sp.getUUID());
    }
}
