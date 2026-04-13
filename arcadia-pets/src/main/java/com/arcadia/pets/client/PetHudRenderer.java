package com.arcadia.pets.client;

import com.arcadia.lib.client.ArcadiaTheme;
import com.arcadia.pets.ArcadiaPets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Steampunk-themed pet HUD widget: portrait + pet name + HP bar + hunger + aftershock.
 * Draggable via HUD settings. Only visible when a pet is active.
 */
@EventBusSubscriber(modid = ArcadiaPets.MOD_ID, value = Dist.CLIENT)
public final class PetHudRenderer {

    private static final int PORTRAIT_W = 38;
    private static final int PORTRAIT_H = 38;
    private static final int BAR_W      = PORTRAIT_W;
    private static final int HP_BAR_H   = 5;
    private static final int HUNGER_H   = 3;
    private static final int AS_BAR_H   = 4;

    // Cached HP string to avoid allocation every frame
    private static String cachedHpStr = "";
    private static int lastHpInt = -1, lastMaxHpInt = -1;

    private PetHudRenderer() {}

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        if (!ClientPetState.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        HudSettings.ensureLoaded();
        if (!HudSettings.showPetPortrait) return;
        if (mc.options.hideGui) return;

        ClientPetState.tickDisplayHp();

        GuiGraphics g = event.getGuiGraphics();
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

        // ── Background panel ──────────────────────────────────────────────────
        int nameH = name.isEmpty() ? 0 : 10;
        int totalH = nameH + PORTRAIT_H + 2 + HP_BAR_H
                + (HudSettings.showAftershock && ClientPetState.isAttackMode() ? 2 + AS_BAR_H : 0);
        int panelW = PORTRAIT_W + 4;
        int panelX = ptX - 2;
        int panelY = ptY - 2;

        g.fill(panelX, panelY, panelX + panelW, panelY + totalH + 6, 0xBB0E0B14);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1,
                ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0xAA));
        ArcadiaTheme.drawBorder(g, panelX, panelY, panelW, totalH + 6,
                ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x44));

        int y = ptY;

        // ── Pet name ──────────────────────────────────────────────────────────
        if (!name.isEmpty()) {
            String displayName = name;
            while (mc.font.width(displayName) > PORTRAIT_W && displayName.length() > 3) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            if (!displayName.equals(name)) displayName += ".";
            g.pose().pushPose();
            g.pose().translate(ptX, y, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(mc.font, displayName, 1, 1, 0x22000000, false);
            g.drawString(mc.font, displayName, 0, 0, ArcadiaTheme.BRASS, false);
            g.pose().popPose();
            y += 10;
        }

        // ── Portrait border ───────────────────────────────────────────────────
        int borderColor;
        if (hpPct < 0.25f && hpPct > 0f) {
            long t = System.currentTimeMillis() % 700L;
            borderColor = t < 350L ? 0xEECC3322 : 0x88884422;
        } else {
            borderColor = ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x88);
        }

        g.fill(ptX, y, ptX + PORTRAIT_W, y + PORTRAIT_H, 0xCC0A0814);
        ArcadiaTheme.drawBorder(g, ptX, y, PORTRAIT_W, PORTRAIT_H, borderColor);

        // Entity render
        if (!mobType.isEmpty()) {
            LivingEntity entity = ClientPetCache.getEntity(mobType);
            if (entity != null) {
                float maxDim = Math.max(entity.getBbWidth(), entity.getBbHeight());
                int scale = Math.max(6, Math.min(16, (int)(11f / maxDim)));
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        g, ptX + 1, y + 1, ptX + PORTRAIT_W - 1, y + PORTRAIT_H - 1,
                        scale, 0f, ptX + PORTRAIT_W / 2f, y + PORTRAIT_H / 2f, entity);
            }
        }

        // ── Hunger strip ──────────────────────────────────────────────────────
        float hungerPct = Math.max(0f, Math.min(1f, hunger / 100f));
        int hungerColor;
        if (hungerPct > 0.50f)      hungerColor = 0xBB44AA44;
        else if (hungerPct > 0.20f) hungerColor = 0xBBCC8822;
        else {
            long t = System.currentTimeMillis() % 600L;
            hungerColor = t < 300L ? 0xBBCC2222 : 0xBBFF5544;
        }
        int stripY = y + PORTRAIT_H - HUNGER_H - 1;
        g.fill(ptX + 1, stripY, ptX + PORTRAIT_W - 1, stripY + HUNGER_H, 0x66000000);
        g.fill(ptX + 1, stripY, ptX + 1 + (int)((PORTRAIT_W - 2) * hungerPct), stripY + HUNGER_H, hungerColor);

        y += PORTRAIT_H + 2;

        // ── HP bar ────────────────────────────────────────────────────────────
        if (HudSettings.showHpBar) {
            float dispPct = maxHp > 0 ? Math.max(0f, Math.min(1f, dispHp / maxHp)) : 0f;
            int fillColor = hpPct > 0.60f ? 0xFF44AA44 : hpPct > 0.30f ? 0xFFCC8822 : 0xFFCC3333;

            g.fill(ptX, y, ptX + BAR_W, y + HP_BAR_H, 0x88000000);
            g.fill(ptX, y, ptX + (int)(BAR_W * dispPct), y + HP_BAR_H, fillColor);
            ArcadiaTheme.drawBorder(g, ptX - 1, y - 1, BAR_W + 2, HP_BAR_H + 2,
                    ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x33));

            int hpI = (int) hp, maxI = (int) maxHp;
            if (hpI != lastHpInt || maxI != lastMaxHpInt) {
                cachedHpStr = hpI + "/" + maxI;
                lastHpInt = hpI;
                lastMaxHpInt = maxI;
            }
            g.pose().pushPose();
            g.pose().translate(ptX + BAR_W + 2, y - 1, 0);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(mc.font, cachedHpStr, 0, 0, ArcadiaTheme.TEXT_SECONDARY, false);
            g.pose().popPose();

            y += HP_BAR_H + 2;
        }

        // ── Aftershock bar ────────────────────────────────────────────────────
        if (HudSettings.showAftershock && ClientPetState.isAttackMode()) {
            boolean onCooldown = ClientAftershockState.isOnCooldown();
            int barColor = onCooldown ? 0xFFAA44DD : 0xFF44CC44;
            int fillW = onCooldown ? (int)(BAR_W * ClientAftershockState.getCooldownFraction()) : BAR_W;

            g.fill(ptX, y, ptX + BAR_W, y + AS_BAR_H, 0x88000000);
            g.fill(ptX, y, ptX + fillW, y + AS_BAR_H, barColor);
            ArcadiaTheme.drawBorder(g, ptX - 1, y - 1, BAR_W + 2, AS_BAR_H + 2,
                    ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x33));

            g.pose().pushPose();
            g.pose().translate(ptX + BAR_W + 2, y - 1, 0);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(mc.font, "\u26A1", 0, 0,
                    onCooldown ? 0xFFAA44DD : 0xFF55FF55, false);
            g.pose().popPose();
        }
    }
}
