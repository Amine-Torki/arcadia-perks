package com.arcadia.pets.client;

import com.arcadia.pets.network.C2SDuelAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Renders a small toast overlay in the top-right of the HUD when the local
 * player has an incoming duel challenge, with [Accept] / [Decline] buttons.
 *
 * <p>The overlay auto-dismisses after 60 seconds (matching the server's challenge TTL).</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "arcadia_pets", value = Dist.CLIENT)
public final class DuelInviteOverlay {

    private static String pendingChallenger = null;
    private static long   shownAt           = 0L;
    private static final long SHOW_MS       = 60_000L;

    // HUD dimensions
    private static final int TOAST_W  = 160;
    private static final int TOAST_H  = 40;
    private static final int BTN_W    = 52;
    private static final int BTN_H    = 12;
    private static final int MARGIN   = 8;

    private DuelInviteOverlay() {}

    /** Called from {@link com.arcadia.pets.network.S2CDuelInvite#handle} on the client thread. */
    public static void showInvite(String challengerName) {
        pendingChallenger = challengerName;
        shownAt           = System.currentTimeMillis();
    }

    public static void dismiss() {
        pendingChallenger = null;
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (pendingChallenger == null) return;
        if (System.currentTimeMillis() - shownAt > SHOW_MS) { dismiss(); return; }

        Minecraft mc      = Minecraft.getInstance();
        GuiGraphics g     = event.getGuiGraphics();
        int screenW       = mc.getWindow().getGuiScaledWidth();

        int tx = screenW - TOAST_W - MARGIN;
        int ty = MARGIN;

        // Background panel
        g.fill(tx - 1, ty - 1, tx + TOAST_W + 1, ty + TOAST_H + 1, 0xFF000000);
        g.fill(tx, ty, tx + TOAST_W, ty + TOAST_H, 0xCC1a1a2e);

        // Title
        g.drawString(mc.font, "§e⚔ Duel Challenge!", tx + 4, ty + 4, 0xFFFFDD, false);
        // Challenger name
        g.drawString(mc.font, "§7" + pendingChallenger + " challenges you!", tx + 4, ty + 14, 0xAAAAAA, false);

        // Accept button
        int btnY = ty + TOAST_H - BTN_H - 3;
        int acceptX = tx + 4;
        g.fill(acceptX, btnY, acceptX + BTN_W, btnY + BTN_H, 0xFF2d6a2d);
        g.drawString(mc.font, "§a✔ Accept", acceptX + 4, btnY + 2, 0xFFFFFF, false);

        // Decline button
        int declineX = tx + TOAST_W - BTN_W - 4;
        g.fill(declineX, btnY, declineX + BTN_W, btnY + BTN_H, 0xFF6a2d2d);
        g.drawString(mc.font, "§c✖ Decline", declineX + 4, btnY + 2, 0xFFFFFF, false);

        // Countdown
        long remaining = (SHOW_MS - (System.currentTimeMillis() - shownAt)) / 1000;
        g.drawString(mc.font, "§8(" + remaining + "s)", tx + TOAST_W - 30, ty + 4, 0x888888, false);

        // Mouse click detection — use InputEvent instead for cleaner handling
        // but simple approach: detect via scheduled tick on mouse press
    }

    /**
     * Call this from the mod's mouse-click handling (InputEvent) with the raw screen coords.
     * Returns true if the click was consumed by this overlay.
     */
    public static boolean handleClick(double mouseX, double mouseY) {
        if (pendingChallenger == null) return false;

        Minecraft mc  = Minecraft.getInstance();
        int screenW   = mc.getWindow().getGuiScaledWidth();
        int tx        = screenW - TOAST_W - MARGIN;
        int ty        = MARGIN;
        int btnY      = ty + TOAST_H - BTN_H - 3;

        int acceptX  = tx + 4;
        int declineX = tx + TOAST_W - BTN_W - 4;

        if (mouseX >= acceptX && mouseX <= acceptX + BTN_W && mouseY >= btnY && mouseY <= btnY + BTN_H) {
            PacketDistributor.sendToServer(new C2SDuelAction(C2SDuelAction.ACCEPT_CHALLENGE, "", 0, 0));
            dismiss();
            return true;
        }
        if (mouseX >= declineX && mouseX <= declineX + BTN_W && mouseY >= btnY && mouseY <= btnY + BTN_H) {
            PacketDistributor.sendToServer(new C2SDuelAction(C2SDuelAction.DECLINE_CHALLENGE, "", 0, 0));
            dismiss();
            return true;
        }
        return false;
    }
}
