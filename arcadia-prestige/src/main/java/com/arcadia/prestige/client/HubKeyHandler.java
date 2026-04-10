package com.arcadia.prestige.client;

import com.arcadia.lib.client.ArcadiaHubScreen;
import com.arcadia.prestige.ArcadiaDashboard;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Registers and handles the "Open Arcadia Hub" keybind (default: L).
 * Uses ClientTickEvent (not PlayerTickEvent) to avoid server-thread interference in singleplayer.
 */
@EventBusSubscriber(modid = ArcadiaDashboard.MOD_ID, value = Dist.CLIENT)
public final class HubKeyHandler {

    public static final KeyMapping OPEN_HUB = new KeyMapping(
            "key.arcadia_prestige.open_hub",
            GLFW.GLFW_KEY_L,
            "key.categories.arcadia_prestige"
    );

    private HubKeyHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OPEN_HUB.consumeClick()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        ArcadiaHubScreen.open();
    }
}
