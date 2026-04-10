package com.arcadia.prestige.client;

import com.arcadia.prestige.PrestigeCard;
import com.arcadia.prestige.network.C2SDashboardAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Full-screen /prestige hub — no container, pure client Screen.
 * Four large clickable cards, each navigates to a dashboard tab.
 */
public class PrestigeHubScreen extends Screen {

    // Card dimensions (10% larger than original 80×90)
    private static final int CARD_W = 88;
    private static final int CARD_H = 99;
    private static final int CARD_GAP = 12;
    private static final int CARD_COUNT = PrestigeCard.ALL.size();

    private int hoveredCard = -1;

    public PrestigeHubScreen() {
        super(Component.literal("Arcadia Prestige"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new PrestigeHubScreen());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        // Semi-transparent dark overlay
        g.fill(0, 0, this.width, this.height, 0xAA0A0A14);

        int totalW = CARD_COUNT * CARD_W + (CARD_COUNT - 1) * CARD_GAP;
        int startX = (this.width - totalW) / 2;
        int startY = (this.height - CARD_H) / 2 - 12;

        // Decorative top line
        int lineW = totalW + 40;
        int lineX = (this.width - lineW) / 2;
        g.fill(lineX, startY - 38, lineX + lineW, startY - 37, 0x44FFD700);

        // Title with shadow
        Component title = Component.translatable("arcadia_prestige.hub.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        g.drawCenteredString(this.font, title, this.width / 2 + 1, startY - 27, 0x44000000); // shadow
        g.drawCenteredString(this.font, title, this.width / 2, startY - 28, 0xFFD700);

        Component sub = Component.translatable("arcadia_prestige.hub.subtitle").withStyle(ChatFormatting.GRAY);
        g.drawCenteredString(this.font, sub, this.width / 2, startY - 16, 0x999999);

        // Decorative bottom line below subtitle
        g.fill(lineX, startY - 6, lineX + lineW, startY - 5, 0x22FFFFFF);

        hoveredCard = -1;
        for (int i = 0; i < PrestigeCard.ALL.size(); i++) {
            PrestigeCard card = PrestigeCard.ALL.get(i);
            int cx = startX + i * (CARD_W + CARD_GAP);
            boolean hovered = card.available() && mouseX >= cx && mouseX < cx + CARD_W
                    && mouseY >= startY && mouseY < startY + CARD_H;
            if (hovered) hoveredCard = i;
            drawCard(g, card, cx, startY, CARD_W, CARD_H, hovered, card.available() ? 1.0f : 0.35f);
            if (!card.available()) {
                g.drawCenteredString(this.font,
                        Component.translatable("arcadia_prestige.hub.not_installed"),
                        cx + CARD_W / 2, startY + CARD_H - 14, 0xFF554444);
            }
        }

        // Bottom decorative line
        g.fill(lineX, startY + CARD_H + 8, lineX + lineW, startY + CARD_H + 9, 0x22FFFFFF);

        // Hint at bottom
        Component hint = Component.translatable("arcadia_prestige.hub.close").withStyle(ChatFormatting.DARK_GRAY);
        g.drawCenteredString(this.font, hint, this.width / 2, startY + CARD_H + 18, 0x555555);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * Shared card renderer used by both PrestigeHubScreen and DashboardScreen mini cards.
     * @param textScale 1.0f for hub, 0.8f for dashboard mini cards
     */
    static void drawCard(GuiGraphics g, PrestigeCard card, int x, int y,
                         int cardW, int cardH, boolean hovered, float textScale) {
        var font = Minecraft.getInstance().font;
        int accent = card.color() | 0xFF000000;

        // Outer glow on hover (soft colored shadow)
        if (hovered) {
            int glowColor = (card.color() & 0x00FFFFFF) | 0x22000000;
            g.fill(x - 2, y - 2, x + cardW + 2, y + cardH + 2, glowColor);
            g.fill(x - 1, y - 1, x + cardW + 1, y + cardH + 1, (card.color() & 0x00FFFFFF) | 0x33000000);
        }

        // Drop shadow (offset)
        g.fill(x + 3, y + 3, x + cardW + 3, y + cardH + 3, 0x66000000);

        // Card background — gradient effect (darker at bottom)
        int bgTop    = hovered ? 0xEE1E1E35 : 0xDD141428;
        int bgBottom = hovered ? 0xEE12122A : 0xDD0C0C1E;
        g.fill(x, y, x + cardW, y + cardH / 2, bgTop);
        g.fill(x, y + cardH / 2, x + cardW, y + cardH, bgBottom);

        // Colored top accent bar (thicker on hover)
        int accentH = hovered ? 4 : 3;
        g.fill(x, y, x + cardW, y + accentH, accent);

        // Inner highlight line at top (subtle light)
        g.fill(x + 1, y + accentH, x + cardW - 1, y + accentH + 1,
                hovered ? 0x33FFFFFF : 0x15FFFFFF);

        // Border — glowing on hover, subtle otherwise
        int border = hovered ? accent : 0xFF2A2A44;
        g.hLine(x, x + cardW - 1, y, border);
        g.hLine(x, x + cardW - 1, y + cardH - 1, border);
        g.vLine(x, y, y + cardH - 1, border);
        g.vLine(x + cardW - 1, y, y + cardH - 1, border);

        // Second border (inner) on hover for depth
        if (hovered) {
            int innerBorder = (card.color() & 0x00FFFFFF) | 0x44000000;
            g.hLine(x + 1, x + cardW - 2, y + 1, innerBorder);
            g.hLine(x + 1, x + cardW - 2, y + cardH - 2, innerBorder);
            g.vLine(x + 1, y + 1, y + cardH - 2, innerBorder);
            g.vLine(x + cardW - 2, y + 1, y + cardH - 2, innerBorder);
        }

        int cx = x + cardW / 2;

        // Emoji — larger and centered, with glow effect on hover
        int emojiY = y + cardH / 5;
        float emojiScale = textScale * (hovered ? 1.3f : 1.15f);
        drawScaled(g, font, Component.literal(card.emoji()), cx, emojiY, accent, emojiScale);

        // Separator line below emoji
        int sepY = y + (int) (cardH * 0.34f);
        int sepPad = cardW / 5;
        g.fill(x + sepPad, sepY, x + cardW - sepPad, sepY + 1,
                hovered ? (accent & 0x00FFFFFF) | 0x66000000 : 0x22FFFFFF);

        // Label (with 2-line wrap)
        Component label = Component.translatable(card.labelKey()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        int labelY = y + (int) (cardH * 0.44f);
        float scaledMax = (cardW - 8) / textScale;
        if (font.width(label) <= scaledMax) {
            drawScaled(g, font, label, cx, labelY, hovered ? 0xFFFFFF : 0xDDDDDD, textScale);
        } else {
            String ls = label.getString();
            int lmid = ls.length() / 2;
            int lsplit = nearestSpace(ls, lmid);
            int lineGap = (int) (5 / textScale);
            int labelColor = hovered ? 0xFFFFFF : 0xDDDDDD;
            drawScaled(g, font, Component.literal(ls.substring(0, lsplit)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), cx, labelY - lineGap, labelColor, textScale);
            drawScaled(g, font, Component.literal(ls.substring(lsplit + 1)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), cx, labelY + lineGap, labelColor, textScale);
        }

        // Sub-label (with 2-line wrap)
        Component sub = Component.translatable(card.sublabelKey()).withStyle(ChatFormatting.GRAY);
        int subY = y + (int) (cardH * 0.73f);
        int subColor = hovered ? 0xAAAAAACC : 0x777777;
        if (font.width(sub) <= scaledMax) {
            drawScaled(g, font, sub, cx, subY, subColor, textScale);
        } else {
            String s = sub.getString();
            int mid = s.length() / 2;
            int split = nearestSpace(s, mid);
            int lineGap = (int) (4 / textScale);
            drawScaled(g, font, Component.literal(s.substring(0, split)), cx, subY - lineGap, subColor, textScale);
            drawScaled(g, font, Component.literal(s.substring(split + 1)), cx, subY + lineGap, subColor, textScale);
        }
    }

    /**
     * Returns the index of the space in {@code s} nearest to {@code mid}.
     * Searches both before and after mid; falls back to mid if no space found.
     */
    private static int nearestSpace(String s, int mid) {
        int before = s.lastIndexOf(' ', mid);
        int after  = s.indexOf(' ', mid + 1);
        if (before < 0 && after < 0) return mid;
        if (before < 0) return after;
        if (after  < 0) return before;
        return (mid - before <= after - mid) ? before : after;
    }

    /** Draws a centered Component at (cx, y) with the given scale factor. */
    private static void drawScaled(GuiGraphics g, net.minecraft.client.gui.Font font,
                                   Component text, int cx, int y, int color, float scale) {
        if (scale == 1.0f) {
            g.drawCenteredString(font, text, cx, y, color);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(font, text, 0, 0, color);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredCard >= 0 && PrestigeCard.ALL.get(hoveredCard).available()) {
            int tab = PrestigeCard.ALL.get(hoveredCard).tab();
            PacketDistributor.sendToServer(new C2SDashboardAction(C2SDashboardAction.OPEN_TAB, String.valueOf(tab)));
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void renderBlurredBackground(float partialTick) { /* no blur */ }
}
