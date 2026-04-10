package com.arcadia.prestige.client;

import com.arcadia.prestige.ArcadiaDashboard;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Registers and handles the "Open Arcadia Hub" keybind (default: L).
 * Opens the PrestigeHubScreen directly on the client.
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
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!OPEN_HUB.consumeClick()) return;
        if (Minecraft.getInstance().screen != null) return; // don't open if another screen is open
        PrestigeHubScreen.open();
    }
}
