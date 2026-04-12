package com.arcadia.lib.client;

import com.arcadia.lib.ArcadiaLib;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Hub keybind (default: L) — lives in the lib so it works even without prestige.
 */
@EventBusSubscriber(modid = ArcadiaLib.MOD_ID, value = Dist.CLIENT)
public final class HubKeyHandler {

    public static final KeyMapping OPEN_HUB = new KeyMapping(
            "key.arcadia_lib.open_hub",
            GLFW.GLFW_KEY_L,
            "key.categories.arcadia_lib"
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
