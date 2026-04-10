package com.arcadia.pets.client;

import com.arcadia.pets.ArcadiaPets;
import com.arcadia.pets.network.C2SPetAction;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * Registers and handles the "Open Pet Panel" keybind (default: P).
 * Uses ClientTickEvent to avoid server-thread interference in singleplayer.
 */
@EventBusSubscriber(modid = ArcadiaPets.MOD_ID, value = Dist.CLIENT)
public final class PetKeyHandler {

    public static final KeyMapping OPEN_PET_PANEL = new KeyMapping(
            "key.arcadia_prestige.open_pet_panel",
            GLFW.GLFW_KEY_P,
            "key.categories.arcadia_prestige"
    );

    private PetKeyHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OPEN_PET_PANEL.consumeClick()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        PacketDistributor.sendToServer(new C2SPetAction(C2SPetAction.OPEN_PANEL, new UUID(0, 0)));
    }
}
