package com.arcadia.pets.client;

import com.arcadia.pets.ArcadiaPets;
import com.arcadia.pets.network.C2SPetAction;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * Registers and handles the "Open Pet Panel" keybind (default: P).
 *
 * <p>When pressed, sends {@link C2SPetAction#OPEN_PANEL} to the server, which
 * opens the pet panel for the player's currently active (or pocket) pet.
 * No-ops if no pet is active.</p>
 */
@EventBusSubscriber(modid = ArcadiaPets.MOD_ID, value = Dist.CLIENT)
public final class PetKeyHandler {

    public static final KeyMapping OPEN_PET_PANEL = new KeyMapping(
            "key.arcadia_prestige.open_pet_panel",
            GLFW.GLFW_KEY_P,
            "key.categories.arcadia_prestige"
    );

    private PetKeyHandler() {}

    // ── Key polling (GAME bus) ────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // consumeClick() drains the press queue; only fire once per press even if held
        if (!OPEN_PET_PANEL.consumeClick()) return;

        // Send to server — petId is unused for OPEN_PANEL
        PacketDistributor.sendToServer(new C2SPetAction(C2SPetAction.OPEN_PANEL, new UUID(0, 0)));
    }
}
