package com.arcadia.pets.client;

import com.arcadia.pets.ArcadiaPets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Game-bus handler for the RTT pet icon cache.
 * Flushes pending renders before each frame and clears GPU resources on disconnect.
 */
@EventBusSubscriber(modid = ArcadiaPets.MOD_ID, value = Dist.CLIENT)
public final class PetRenderCacheHandler {

    private PetRenderCacheHandler() {}

    @SubscribeEvent
    public static void onPreFrame(RenderFrameEvent.Pre event) {
        PetRenderCache.flushPending();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PetRenderCache.clearAll();
    }
}
