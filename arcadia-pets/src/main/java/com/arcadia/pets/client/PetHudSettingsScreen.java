package com.arcadia.pets.client;

import com.arcadia.lib.client.ArcadiaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import com.arcadia.pets.network.C2SPetAction;

/**
 * Steampunk-themed HUD settings screen with drag-to-reposition and toggle buttons.
 */
public final class PetHudSettingsScreen extends Screen {

    private static final int PRT_W = 38, PRT_H = 38;
    private static final int BAR_H = 5, AS_H = 4;
    private static final int WGT_H = 10 + PRT_H + 2 + BAR_H + 2 + AS_H;
    private static final int WGT_W = PRT_W + 30;
    private static final int BTN_W = 100, BTN_H = 16, BTN_GAP = 6;

    private boolean dragging = false;
    private int dragOffX, dragOffY;
    private int ptX, ptY;
    private boolean showHp, showAs, showPt, reducedMotion;

    public PetHudSettingsScreen() {
        super(Component.translatable("arcadia_pets.gui.hud_settings.title"));
    }

    @Override
    protected void init() {
        HudSettings.ensureLoaded();
        ptX = HudSettings.resolvePortraitX(this.width);
        ptY = HudSettings.resolvePortraitY(this.height);
        showHp        = HudSettings.showHpBar;
        showAs        = HudSettings.showAftershock;
        showPt        = HudSettings.showPetPortrait;
        reducedMotion = HudSettings.reducedMotion;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, ArcadiaTheme.OVERLAY_BG);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;

        // Title
        ArcadiaTheme.drawTitleBar(g, Component.translatable("arcadia_pets.gui.hud_settings.header"),
                cx, 10, this.width - 60);
        g.drawCenteredString(this.font,
                Component.translatable("arcadia_pets.gui.hud_settings.subtitle"),
                cx, 24, ArcadiaTheme.TEXT_DIM);

        renderWidgetPreview(g, mouseX, mouseY);

        int totalW = BTN_W * 3 + BTN_GAP * 2;
        int startX = cx - totalW / 2;
        int btnY = this.height - 54;

        // Toggle buttons
        drawToggleBtn(g, mouseX, mouseY, startX, btnY, BTN_W,
                Component.translatable("arcadia_pets.gui.hud_settings.portrait").getString(), showPt);
        drawToggleBtn(g, mouseX, mouseY, startX + BTN_W + BTN_GAP, btnY, BTN_W,
                Component.translatable("arcadia_pets.gui.hud_settings.hp_bar").getString(), showHp);
        drawToggleBtn(g, mouseX, mouseY, startX + (BTN_W + BTN_GAP) * 2, btnY, BTN_W,
                Component.translatable("arcadia_pets.gui.hud_settings.aftershock").getString(), showAs);

        // Reduced motion
        drawToggleBtn(g, mouseX, mouseY, startX, btnY - BTN_H - BTN_GAP, totalW,
                "Epilepsy-safe mode", reducedMotion);

        // Action buttons
        int actY = btnY + BTN_H + BTN_GAP;
        int actW = (totalW - BTN_GAP * 2) / 3;
        drawActionBtn(g, mouseX, mouseY, startX, actY, actW,
                "\u00ab /pets", ArcadiaTheme.BORDER_IDLE);
        drawActionBtn(g, mouseX, mouseY, startX + actW + BTN_GAP, actY, actW,
                Component.translatable("arcadia_pets.gui.hud_settings.reset").getString(),
                ArcadiaTheme.darken(ArcadiaTheme.COPPER, 40));
        drawActionBtn(g, mouseX, mouseY, startX + (actW + BTN_GAP) * 2, actY, actW,
                Component.translatable("arcadia_pets.gui.hud_settings.done").getString(),
                ArcadiaTheme.darken(ArcadiaTheme.PATINA, 60));
    }

    private void renderWidgetPreview(GuiGraphics g, int mx, int my) {
        int alpha = showPt ? 0xFF : 0x55;
        boolean hover = isOverWidget(mx, my);

        if (hover || dragging) {
            ArcadiaTheme.drawGlow(g, ptX - 2, ptY - 2, PRT_W + 4, WGT_H + 4, ArcadiaTheme.COPPER);
        }

        g.fill(ptX - 2, ptY - 2, ptX + PRT_W + 2, ptY + WGT_H + 2,
                ArcadiaTheme.withAlpha(0x0E0B14, 0xBB));
        ArcadiaTheme.drawBorder(g, ptX - 2, ptY - 2, PRT_W + 4, WGT_H + 4,
                ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, hover ? 0x88 : 0x44));

        int x = ptX, y = ptY;

        // Name
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(this.font, "Pet Name", 0, 0, ArcadiaTheme.withAlpha(ArcadiaTheme.BRASS, alpha), false);
        g.pose().popPose();
        y += 10;

        // Portrait
        g.fill(x, y, x + PRT_W, y + PRT_H, ArcadiaTheme.withAlpha(0x0A0814, alpha));
        ArcadiaTheme.drawBorder(g, x, y, PRT_W, PRT_H, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, alpha / 2));
        g.pose().pushPose();
        g.pose().translate(x + PRT_W / 2f, y + PRT_H / 2f - 4, 0);
        g.pose().scale(0.7f, 0.7f, 1f);
        g.drawCenteredString(this.font,
                Component.translatable("arcadia_pets.gui.hud_settings.portrait_preview"),
                0, 0, ArcadiaTheme.withAlpha(ArcadiaTheme.TEXT_DIM, alpha));
        g.pose().popPose();
        y += PRT_H + 2;

        // HP bar
        if (showHp) {
            g.fill(x, y, x + PRT_W, y + BAR_H, ArcadiaTheme.withAlpha(0x000000, alpha * 2 / 3));
            g.fill(x, y, x + (int)(PRT_W * 0.65f), y + BAR_H, ArcadiaTheme.withAlpha(0x44AA44, alpha));
            ArcadiaTheme.drawBorder(g, x - 1, y - 1, PRT_W + 2, BAR_H + 2,
                    ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x33));
            y += BAR_H + 2;
        }

        // Aftershock bar
        if (showAs) {
            g.fill(x, y, x + PRT_W, y + AS_H, ArcadiaTheme.withAlpha(0x000000, alpha * 2 / 3));
            g.fill(x, y, x + (int)(PRT_W * 0.55f), y + AS_H, ArcadiaTheme.withAlpha(0xAA44DD, alpha));
            ArcadiaTheme.drawBorder(g, x - 1, y - 1, PRT_W + 2, AS_H + 2,
                    ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x33));
        }

        if (hover || dragging) {
            g.drawString(this.font,
                    Component.translatable("arcadia_pets.gui.hud_settings.drag_hint"),
                    ptX + PRT_W + 6, ptY + PRT_H / 2,
                    ArcadiaTheme.withAlpha(ArcadiaTheme.TEXT_SECONDARY, 0xAA), false);
        }
    }

    private void drawToggleBtn(GuiGraphics g, int mx, int my, int x, int y, int w,
                               String label, boolean on) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + BTN_H;
        int bg = on ? 0xFF1A3A1A : 0xFF3A1A1A;
        if (hover) bg = ArcadiaTheme.brighten(bg, 25);
        g.fill(x, y, x + w, y + BTN_H, bg);
        ArcadiaTheme.drawBorder(g, x, y, w, BTN_H, hover ? ArcadiaTheme.COPPER : ArcadiaTheme.BORDER_IDLE);

        int dotX = x + 3, dotY = y + 4, dotS = 8;
        g.fill(dotX, dotY, dotX + dotS, dotY + dotS, on ? 0xFF2A4A2A : 0xFF3A2020);
        ArcadiaTheme.drawBorder(g, dotX, dotY, dotS, dotS, on ? 0xFF44AA44 : 0xFF664444);
        if (on) g.fill(dotX + 2, dotY + 2, dotX + dotS - 2, dotY + dotS - 2, 0xFF55CC55);

        g.pose().pushPose();
        g.pose().translate(x + dotS + 8, y + 4, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(this.font, label, 0, 0, ArcadiaTheme.TEXT_PRIMARY, false);
        g.pose().popPose();
    }

    private void drawActionBtn(GuiGraphics g, int mx, int my, int x, int y, int w,
                               String label, int bgColor) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + BTN_H;
        int bg = hover ? ArcadiaTheme.brighten(bgColor, 30) : bgColor;
        g.fill(x, y, x + w, y + BTN_H, bg);
        ArcadiaTheme.drawBorder(g, x, y, w, BTN_H, hover ? ArcadiaTheme.COPPER : ArcadiaTheme.BORDER_IDLE);
        g.drawCenteredString(this.font, label, x + w / 2, y + 4, ArcadiaTheme.TEXT_PRIMARY);
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return false;
        int x = (int) mx, y = (int) my;
        int cx = this.width / 2;
        int totalW = BTN_W * 3 + BTN_GAP * 2;
        int startX = cx - totalW / 2;
        int btnY = this.height - 54;

        int rmY = btnY - BTN_H - BTN_GAP;
        if (inBtn(x, y, startX, rmY, totalW, BTN_H)) { playClick(); reducedMotion = !reducedMotion; return true; }

        if (inBtn(x, y, startX, btnY, BTN_W, BTN_H)) { playClick(); showPt = !showPt; return true; }
        if (inBtn(x, y, startX + BTN_W + BTN_GAP, btnY, BTN_W, BTN_H)) { playClick(); showHp = !showHp; return true; }
        if (inBtn(x, y, startX + (BTN_W + BTN_GAP) * 2, btnY, BTN_W, BTN_H)) { playClick(); showAs = !showAs; return true; }

        int actY = btnY + BTN_H + BTN_GAP;
        int actW = (totalW - BTN_GAP * 2) / 3;
        if (inBtn(x, y, startX, actY, actW, BTN_H)) {
            playClick(); closeAndSave();
            PacketDistributor.sendToServer(new C2SPetAction(C2SPetAction.OPEN_PANEL, new java.util.UUID(0, 0)));
            return true;
        }
        if (inBtn(x, y, startX + actW + BTN_GAP, actY, actW, BTN_H)) { playClick(); resetPositions(); return true; }
        if (inBtn(x, y, startX + (actW + BTN_GAP) * 2, actY, actW, BTN_H)) { playClick(); closeAndSave(); return true; }

        if (isOverWidget(x, y)) { dragging = true; dragOffX = x - ptX; dragOffY = y - ptY; return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && dragging) {
            ptX = Math.max(0, Math.min((int) mx - dragOffX, this.width - WGT_W - 4));
            ptY = Math.max(0, Math.min((int) my - dragOffY, this.height - WGT_H - 4));
            return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) { dragging = false; return false; }
    @Override public void onClose() { closeAndSave(); }
    @Override public boolean isPauseScreen() { return false; }

    private void resetPositions() {
        HudSettings.resetPositions();
        ptX = HudSettings.resolvePortraitX(this.width);
        ptY = HudSettings.resolvePortraitY(this.height);
        showPt = true; showHp = true; showAs = true;
    }

    private void closeAndSave() {
        HudSettings.petPortraitXFrac = (float) ptX / this.width;
        HudSettings.petPortraitYFrac = (float) ptY / this.height;
        HudSettings.showHpBar       = showHp;
        HudSettings.showAftershock  = showAs;
        HudSettings.showPetPortrait = showPt;
        HudSettings.reducedMotion   = reducedMotion;
        HudSettings.save();
        Minecraft.getInstance().setScreen(null);
    }

    private boolean isOverWidget(int x, int y) {
        return x >= ptX - 4 && x < ptX + WGT_W + 4 && y >= ptY - 4 && y < ptY + WGT_H + 4;
    }

    private static boolean inBtn(int x, int y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }
}
