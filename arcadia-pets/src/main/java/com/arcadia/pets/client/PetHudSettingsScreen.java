package com.arcadia.pets.client;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import com.arcadia.pets.network.C2SPetAction;

/**
 * Full-screen drag-to-reposition overlay for the pet HUD widget.
 * The widget contains the portrait, HP bar, and aftershock bar as a single group.
 * Opened via the ⚙ gear button on the pet card.
 */
public final class PetHudSettingsScreen extends Screen {

    private static final int PRT_W = 36;  // portrait preview width
    private static final int PRT_H = 36;  // portrait preview height
    private static final int BAR_H = 5;   // HP bar height
    private static final int AS_H  = 4;   // aftershock bar height
    /** Total widget height (name + portrait + HP + aftershock). */
    private static final int WGT_H = 9 + PRT_H + 1 + BAR_H + 2 + AS_H;
    private static final int WGT_W = PRT_W + 30; // portrait + label right side

    private boolean dragging = false;
    private int dragOffX, dragOffY;

    private int ptX, ptY;
    private boolean showHp;
    private boolean showAs;
    private boolean showPt;

    public PetHudSettingsScreen() {
        super(Component.translatable("arcadia_prestige.gui.hud_settings.title"));
    }

    @Override
    protected void init() {
        HudSettings.ensureLoaded();
        ptX    = HudSettings.resolvePortraitX(this.width);
        ptY    = HudSettings.resolvePortraitY(this.height);
        showHp = HudSettings.showHpBar;
        showAs = HudSettings.showAftershock;
        showPt = HudSettings.showPetPortrait;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xAA000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);

        Component title = Component.translatable("arcadia_prestige.gui.hud_settings.header");
        g.drawString(this.font, title, (this.width - this.font.width(title)) / 2, 10, 0xFFFFFF, false);
        Component subtitle = Component.translatable("arcadia_prestige.gui.hud_settings.subtitle");
        g.drawString(this.font, subtitle, (this.width - this.font.width(subtitle)) / 2, 22, 0xAAAAAA, false);

        renderWidgetPreview(g, mouseX, mouseY);

        // Row 1 (toggles): Portrait + HP Bar + Aftershock — total 110+4+100+4+108 = 326, start at cx-163
        int btnY = this.height - 38;
        int cx   = this.width / 2;
        int r1x  = cx - 163;
        renderBtn(g, mouseX, mouseY, r1x,       btnY, 110, 14,
                (showPt ? "\u2714 " : "\u2716 ") + Component.translatable("arcadia_prestige.gui.hud_settings.portrait").getString(),
                showPt ? 0xFF226622 : 0xFF662222);
        renderBtn(g, mouseX, mouseY, r1x + 114, btnY, 100, 14,
                (showHp ? "\u2714 " : "\u2716 ") + Component.translatable("arcadia_prestige.gui.hud_settings.hp_bar").getString(),
                showHp ? 0xFF226622 : 0xFF662222);
        renderBtn(g, mouseX, mouseY, r1x + 218, btnY, 108, 14,
                (showAs ? "\u2714 " : "\u2716 ") + Component.translatable("arcadia_prestige.gui.hud_settings.aftershock").getString(),
                showAs ? 0xFF226622 : 0xFF662222);
        // Row 2 (actions): Back + Reset + Done — total 70+4+100+4+60 = 238, start at cx-119
        int btnY2 = btnY + 18;
        int r2x   = cx - 119;
        renderBtn(g, mouseX, mouseY, r2x,       btnY2, 70, 14,
                "§7← §e/pets", 0xFF222233);
        renderBtn(g, mouseX, mouseY, r2x + 74,  btnY2, 100, 14,
                Component.translatable("arcadia_prestige.gui.hud_settings.reset").getString(), 0xFF553311);
        renderBtn(g, mouseX, mouseY, r2x + 178, btnY2, 60, 14,
                Component.translatable("arcadia_prestige.gui.hud_settings.done").getString(), 0xFF334466);
    }

    private void renderWidgetPreview(GuiGraphics g, int mx, int my) {
        int alpha = showPt ? 0xFF : 0x55;
        boolean hover = isOverWidget(mx, my);
        int x = ptX, y = ptY;

        // Highlight box when hovered
        if (hover || dragging) {
            g.fill(x - 4, y - 4, x + WGT_W + 4, y + WGT_H + 4, withAlpha(0xFFFFFF, 0x33));
        }

        // Name placeholder
        g.drawString(this.font, "Pet Name", x, y, withAlpha(0xFFFFAA, alpha), false);
        int boxY = y + 9;

        // Portrait box
        g.fill(x, boxY, x + PRT_W, boxY + PRT_H, withAlpha(0x080812, alpha));
        g.fill(x, boxY,              x + PRT_W, boxY + 1,         withAlpha(0xFFFFFF, alpha / 2));
        g.fill(x, boxY + PRT_H - 1, x + PRT_W, boxY + PRT_H,     withAlpha(0xFFFFFF, alpha / 2));
        g.fill(x, boxY,              x + 1,     boxY + PRT_H,     withAlpha(0xFFFFFF, alpha / 2));
        g.fill(x + PRT_W - 1, boxY, x + PRT_W, boxY + PRT_H,     withAlpha(0xFFFFFF, alpha / 2));
        // Portrait label
        Component ptLabel = Component.translatable("arcadia_prestige.gui.hud_settings.portrait_preview");
        g.drawString(this.font, ptLabel,
                x + (PRT_W - this.font.width(ptLabel)) / 2, boxY + PRT_H / 2 - 4,
                withAlpha(0xCCCCCC, alpha), false);

        int barY = boxY + PRT_H + 1;

        // HP bar preview
        if (showHp) {
            int hpAlpha = alpha;
            g.fill(x, barY, x + PRT_W, barY + BAR_H, withAlpha(0x000000, (hpAlpha * 2 / 3)));
            g.fill(x, barY, x + (int)(PRT_W * 0.65f), barY + BAR_H, withAlpha(0xAA4444, hpAlpha));
            g.drawString(this.font, "24/40", x + PRT_W + 2, barY - 1, withAlpha(0xDDDDDD, hpAlpha), false);
            barY += BAR_H + 2;
        }

        // Aftershock bar preview
        if (showAs) {
            int asAlpha = alpha;
            g.fill(x, barY, x + PRT_W, barY + AS_H, withAlpha(0x000000, (asAlpha * 2 / 3)));
            g.fill(x, barY, x + (int)(PRT_W * 0.55f), barY + AS_H, withAlpha(0xCC44FF, asAlpha));
            g.drawString(this.font, "\u26A1", x + PRT_W + 2, barY - 1, withAlpha(0xCC44FF, asAlpha), false);
        }

        if (hover || dragging) {
            Component hint = Component.translatable("arcadia_prestige.gui.hud_settings.drag_hint");
            g.drawString(this.font, hint, x + WGT_W + 6, y + PRT_H / 2, 0x88FFFFFF, false);
        }
    }

    private void renderBtn(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, int bg) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hover ? brighten(bg) : bg);
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, 0xFFFFFF, false);
    }

    private static void playClick() {
        var p = Minecraft.getInstance().player;
        if (p != null) p.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return false;
        int x = (int) mx, y = (int) my;

        int btnY = this.height - 38;
        int cx   = this.width / 2;
        int r1x  = cx - 163;

        if (inBtn(x, y, r1x,       btnY, 110, 14)) { playClick(); showPt = !showPt; return true; }
        if (inBtn(x, y, r1x + 114, btnY, 100, 14)) { playClick(); showHp = !showHp; return true; }
        if (inBtn(x, y, r1x + 218, btnY, 108, 14)) { playClick(); showAs = !showAs; return true; }

        int btnY2 = btnY + 18;
        int r2x   = cx - 119;
        if (inBtn(x, y, r2x,       btnY2, 70,  14)) { playClick(); closeAndSave(); PacketDistributor.sendToServer(new C2SPetAction(C2SPetAction.OPEN_PANEL, new java.util.UUID(0, 0))); return true; }
        if (inBtn(x, y, r2x + 74,  btnY2, 100, 14)) { playClick(); resetPositions(); return true; }
        if (inBtn(x, y, r2x + 178, btnY2, 60,  14)) { playClick(); closeAndSave(); return true; }

        if (isOverWidget(x, y)) {
            dragging = true;
            dragOffX = x - ptX;
            dragOffY = y - ptY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && dragging) {
            ptX = clamp((int) mx - dragOffX, 0, this.width  - WGT_W - 4);
            ptY = clamp((int) my - dragOffY, 0, this.height - WGT_H - 4);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragging = false;
        return false;
    }

    @Override
    public void onClose() { closeAndSave(); }

    private void resetPositions() {
        HudSettings.resetPositions();
        ptX    = HudSettings.resolvePortraitX(this.width);
        ptY    = HudSettings.resolvePortraitY(this.height);
        showPt = true;
        showHp = true;
        showAs = true;
    }

    private void closeAndSave() {
        HudSettings.petPortraitXFrac = (float) ptX / this.width;
        HudSettings.petPortraitYFrac = (float) ptY / this.height;
        HudSettings.showHpBar        = showHp;
        HudSettings.showAftershock   = showAs;
        HudSettings.showPetPortrait  = showPt;
        HudSettings.save();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private boolean isOverWidget(int x, int y) {
        return x >= ptX - 4 && x < ptX + WGT_W + 4
                && y >= ptY - 4 && y < ptY + WGT_H + 4;
    }
    private static boolean inBtn(int x, int y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int withAlpha(int rgb, int alpha) {
        return (Math.min(255, alpha) << 24) | (rgb & 0xFFFFFF);
    }
    private static int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((color >> 8)  & 0xFF) + 40);
        int b = Math.min(255, ( color        & 0xFF) + 40);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
