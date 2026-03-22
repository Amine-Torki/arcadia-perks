package com.arcadia.prestige.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.UUID;

/**
 * Indefinite live effect preview mode.
 * <p>
 * Clicking the spyglass in the Cosmetics tab enters preview: third-person camera
 * is forced, the local {@link PlayerEffectCache} is overridden (server-side and
 * other clients see nothing), and the player can walk/sneak/jump freely to see all
 * particle animations. Scroll wheel cycles through all 25 effects (including
 * grade-locked ones — preview only). Any screen opening (ESC, inventory…) exits.
 */
@EventBusSubscriber(modid = "arcadia_prestige", value = Dist.CLIENT)
public final class EffectPreviewHandler {

    /** All effects in display order, preview ignores grade locks. */
    public static final List<String> ALL_EFFECTS = List.of(
            "orbit", "aura", "wings", "storm", "platform",
            "comet", "pulsar", "binary", "nova", "galaxy",
            "snow", "void", "dragon", "helix", "meteor",
            "trail", "hearts", "enchant", "flame", "stars",
            "bubble", "sakura", "rainbow", "ghost", "shockwave"
    );

    private static final List<String> MOVEMENT_EFFECTS = List.of(
            "trail", "hearts", "enchant", "flame", "stars",
            "bubble", "sakura", "rainbow", "ghost", "shockwave"
    );

    private static boolean active      = false;
    private static int     effectIdx   = 0;

    private static CameraType originalCamera;
    private static String     originalEffect;
    private static UUID       localUUID;

    private EffectPreviewHandler() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Enters preview mode starting on the given effect (or the first effect if
     * {@code startEffect} is null/empty).
     */
    public static void startPreview(String startEffect) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        localUUID      = mc.player.getUUID();
        originalCamera = mc.options.getCameraType();
        originalEffect = PlayerEffectCache.getEffect(localUUID);

        effectIdx = startEffect != null ? ALL_EFFECTS.indexOf(startEffect) : -1;
        if (effectIdx < 0) effectIdx = 0;

        applyCurrentEffect(mc);
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        active = true;
        mc.setScreen(null); // close dashboard
    }

    public static boolean isActive() { return active; }

    public static void stop() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.setCameraType(originalCamera);
        if (localUUID != null) {
            PlayerEffectCache.update(localUUID, originalEffect);
        }
        active = false;
    }

    // -------------------------------------------------------------------------
    // Tick — exit when any screen opens
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getUUID().equals(localUUID)) {
            stop();
            return;
        }
        // Any opened screen (pause, inventory…) ends preview
        if (mc.screen != null) {
            stop();
        }
    }

    // -------------------------------------------------------------------------
    // Scroll wheel — cycle effects
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!active) return;
        event.setCanceled(true); // prevent hotbar slot change
        Minecraft mc = Minecraft.getInstance();
        int dir = event.getScrollDeltaY() > 0 ? -1 : 1;
        effectIdx = (effectIdx + dir + ALL_EFFECTS.size()) % ALL_EFFECTS.size();
        applyCurrentEffect(mc);
    }

    // -------------------------------------------------------------------------
    // HUD overlay
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (!active) return;
        if (!event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        String effect  = ALL_EFFECTS.get(effectIdx);
        boolean moving = MOVEMENT_EFFECTS.contains(effect);

        String line1 = capitalize(effect) + "  [" + (effectIdx + 1) + "/" + ALL_EFFECTS.size() + "]";
        String inventoryKey = net.minecraft.client.Minecraft.getInstance().options.keyInventory.getTranslatedKeyMessage().getString();
        String line2 = moving ? "Move to trigger this effect  \u2014  Scroll to cycle  \u2014  " + inventoryKey + " to exit"
                              : "Scroll to cycle  \u2014  " + inventoryKey + " to exit";

        int tw1  = mc.font.width(line1);
        int tw2  = mc.font.width(line2);
        int boxW = Math.max(tw1, tw2) + 16;
        int boxH = 26;
        int bx   = (sw - boxW) / 2;
        int by   = sh - 52;

        g.fill(bx,     by,     bx + boxW, by + boxH, 0xBB000000);
        g.fill(bx,     by,     bx + boxW, by + 1,    0xFF4488FF);
        g.drawString(mc.font, line1, bx + (boxW - tw1) / 2, by + 5,  0xFFFFFF, false);
        g.drawString(mc.font, line2, bx + (boxW - tw2) / 2, by + 15, 0x888888, false);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void applyCurrentEffect(Minecraft mc) {
        PlayerEffectCache.update(localUUID, ALL_EFFECTS.get(effectIdx));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
