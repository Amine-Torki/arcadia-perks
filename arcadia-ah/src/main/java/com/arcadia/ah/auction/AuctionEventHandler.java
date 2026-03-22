package com.arcadia.ah.auction;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.arcadia.ah.ArcadiaAH;

@EventBusSubscriber(modid = ArcadiaAH.MOD_ID)
public final class AuctionEventHandler {

    /** Refresh cache every 30 seconds (600 ticks). */
    private static int tickCounter = 0;
    /** Sweep expired listings every 5 minutes (6000 ticks). */
    private static int expireCounter = 0;

    private AuctionEventHandler() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        // Drain any pending mailbox entries for this player
        AuctionManager.drainMailbox(sp, sp.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        expireCounter++;

        if (tickCounter >= 600) {
            tickCounter = 0;
            AuctionManager.refreshCache();
        }
        if (expireCounter >= 6000) {
            expireCounter = 0;
            AuctionManager.processExpired(event.getServer());
        }
    }
}
