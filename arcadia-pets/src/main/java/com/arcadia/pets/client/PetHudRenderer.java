package com.arcadia.pets.client;

import com.arcadia.pets.ArcadiaPets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Renders the pet HUD widget: portrait (36×36) + pet name label + HP bar + aftershock bar.
 * All elements move together as a single draggable group.
 * Positioned and toggled via {@link HudSettings}. Only visible when a pet is active.
 */
@EventBusSubscriber(modid = ArcadiaPets.MOD_ID, value = Dist.CLIENT)
public final class PetHudRenderer {

    /** Portrait box size (25% smaller than original 48). */
    private static final int PORTRAIT_W = 36;
    private static final int PORTRAIT_H = 36;
    /** HP and aftershock bars span the same width as the portrait. */
    private static final int BAR_W      = PORTRAIT_W;
    private static final int HP_BAR_H   = 5;
    private static final int AS_BAR_H   = 4;

    private PetHudRenderer() {}

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        if (!ClientPetState.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        HudSettings.ensureLoaded();
        if (!HudSettings.showPetPortrait) return;

        // Advance the smoothed HP display value each render frame
        ClientPetState.tickDisplayHp();

        GuiGraphics g  = event.getGuiGraphics();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int ptX = HudSettings.resolvePortraitX(sw);
        int ptY = HudSettings.resolvePortraitY(sh);

        String name    = ClientPetState.getPetName();
        String mobType = ClientPetState.getMobType();
        float  hp      = ClientPetState.getCurrentHp();
        float  maxHp   = ClientPetState.getMaxHp();
        float  dispHp  = ClientPetState.getDisplayHp();
        int    hunger  = ClientPetState.getHunger();
        float  hpPct   = maxHp > 0 ? Math.max(0f, Math.min(1f, hp / maxHp)) : 0f;

        // ── Pet name above the portrait ───────────────────────────────────────
        int nameH = 0;
        if (!name.isEmpty()) {
            g.drawString(mc.font, name, ptX, ptY, 0xFFFFAA, false);
            nameH = 9;
        }

        int boxY = ptY + nameH;

        // ── Portrait border: pulses red when HP < 25% ─────────────────────────
        int borderColor;
        if (hpPct < 0.25f && hpPct > 0f) {
            long t = System.currentTimeMillis() % 700L;
            borderColor = t < 350L ? 0xEEFF2222 : 0x55FF2222;
        } else {
            borderColor = 0x66FFFFFF;
        }

        // ── Portrait box ──────────────────────────────────────────────────────
        g.fill(ptX,                  boxY,                  ptX + PORTRAIT_W, boxY + PORTRAIT_H, 0xCC080812);
        g.fill(ptX,                  boxY,                  ptX + PORTRAIT_W, boxY + 1,              borderColor);
        g.fill(ptX,                  boxY + PORTRAIT_H - 1, ptX + PORTRAIT_W, boxY + PORTRAIT_H,     borderColor);
        g.fill(ptX,                  boxY,                  ptX + 1,          boxY + PORTRAIT_H,      borderColor);
        g.fill(ptX + PORTRAIT_W - 1, boxY,                  ptX + PORTRAIT_W, boxY + PORTRAIT_H,      borderColor);

        // Entity portrait — live render (client-side cost only, acceptable)
        if (!mobType.isEmpty()) {
            LivingEntity entity = ClientPetCache.getEntity(mobType);
            if (entity != null) {
                float maxDim = Math.max(entity.getBbWidth(), entity.getBbHeight());
                int scale = Math.max(6, Math.min(16, (int)(11f / maxDim)));
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        g, ptX + 1, boxY + 1, ptX + PORTRAIT_W - 1, boxY + PORTRAIT_H - 1,
                        scale, 0f, ptX + PORTRAIT_W / 2f, boxY + PORTRAIT_H / 2f, entity);
            }
        }

        // ── Hunger strip (3px, inside portrait bottom edge) ───────────────────
        float hungerPct = Math.max(0f, Math.min(1f, hunger / 100f));
        int hungerColor;
        if (hungerPct > 0.50f) {
            hungerColor = 0xBB44CC44;
        } else if (hungerPct > 0.20f) {
            hungerColor = 0xBBDDAA22;
        } else {
            long t = System.currentTimeMillis() % 600L;
            hungerColor = t < 300L ? 0xBBDD2222 : 0xBBFF6666;
        }
        int stripY = boxY + PORTRAIT_H - 4;
        g.fill(ptX + 1, stripY, ptX + PORTRAIT_W - 1, boxY + PORTRAIT_H - 1, 0x55000000);
        g.fill(ptX + 1, stripY, ptX + 1 + (int)((PORTRAIT_W - 2) * hungerPct), boxY + PORTRAIT_H - 1, hungerColor);

        int y = boxY + PORTRAIT_H + 1;

        // ── HP bar (smoothly animated) ────────────────────────────────────────
        if (HudSettings.showHpBar) {
            float dispPct  = maxHp > 0 ? Math.max(0f, Math.min(1f, dispHp / maxHp)) : 0f;
            int fillColor  = hpPct > 0.60f ? 0xFFAA4444 : hpPct > 0.30f ? 0xFFCC7722 : 0xFFDD3333;
            g.fill(ptX, y, ptX + BAR_W, y + HP_BAR_H, 0xAA000000);
            g.fill(ptX, y, ptX + (int)(BAR_W * dispPct), y + HP_BAR_H, fillColor);

            // HP numbers to the right of the bar at 0.65 scale
            String hpStr = (int)hp + "/" + (int)maxHp;
            g.pose().pushPose();
            g.pose().translate(ptX + BAR_W + 2, y - 1, 0);
            g.pose().scale(0.65f, 0.65f, 1f);
            g.drawString(mc.font, hpStr, 0, 0, 0xDDDDDD, false);
            g.pose().popPose();

            y += HP_BAR_H + 2;
        }

        // ── Aftershock bar (only in ATK mode) ─────────────────────────────────
        if (HudSettings.showAftershock && ClientPetState.isAttackMode()) {
            boolean onCooldown = ClientAftershockState.isOnCooldown();
            int barColor;
            int fillW;
            if (onCooldown) {
                float frac = ClientAftershockState.getCooldownFraction();
                fillW    = (int)(BAR_W * frac);
                barColor = 0xFFCC44FF;
            } else {
                fillW    = BAR_W;
                barColor = 0xFF44CC44;
            }
            g.fill(ptX, y, ptX + BAR_W, y + AS_BAR_H, 0xAA000000);
            g.fill(ptX, y, ptX + fillW, y + AS_BAR_H, barColor);
            g.drawString(mc.font, "\u26A1", ptX + BAR_W + 2, y - 1,
                    onCooldown ? 0xFFCC44FF : 0xFF55FF55, false);
        }
    }
}
