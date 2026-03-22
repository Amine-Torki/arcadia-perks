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

        int totalW = CARD_COUNT * CARD_W + (CARD_COUNT - 1) * CARD_GAP;
        int startX = (this.width - totalW) / 2;
        int startY = (this.height - CARD_H) / 2 - 12;

        // Title
        Component title = Component.translatable("arcadia_prestige.hub.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        g.drawCenteredString(this.font, title, this.width / 2, startY - 28, 0xFFD700);

        Component sub = Component.translatable("arcadia_prestige.hub.subtitle").withStyle(ChatFormatting.GRAY);
        g.drawCenteredString(this.font, sub, this.width / 2, startY - 16, 0x888888);

        hoveredCard = -1;
        for (int i = 0; i < PrestigeCard.ALL.size(); i++) {
            PrestigeCard card = PrestigeCard.ALL.get(i);
            int cx = startX + i * (CARD_W + CARD_GAP);
            boolean hovered = mouseX >= cx && mouseX < cx + CARD_W
                    && mouseY >= startY && mouseY < startY + CARD_H;
            if (hovered) hoveredCard = i;
            drawCard(g, card, cx, startY, CARD_W, CARD_H, hovered, 1.0f);
        }

        // Hint at bottom
        Component hint = Component.translatable("arcadia_prestige.hub.close").withStyle(ChatFormatting.DARK_GRAY);
        g.drawCenteredString(this.font, hint, this.width / 2, startY + CARD_H + 16, 0x555555);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * Shared card renderer used by both PrestigeHubScreen and DashboardScreen mini cards.
     * @param textScale 1.0f for hub, 0.8f for dashboard mini cards
     */
    static void drawCard(GuiGraphics g, PrestigeCard card, int x, int y,
                         int cardW, int cardH, boolean hovered, float textScale) {
        var font = Minecraft.getInstance().font;

        // Shadow
        g.fill(x + 3, y + 3, x + cardW + 3, y + cardH + 3, 0x55000000);

        // Card background
        int bg = hovered ? 0xEE1A1A2E : 0xCC111122;
        g.fill(x, y, x + cardW, y + cardH, bg);

        // Colored top accent bar
        int accent = card.color() | 0xFF000000;
        g.fill(x, y, x + cardW, y + 3, accent);

        // Border
        int border = hovered ? (card.color() | 0xFF000000) : 0xFF333355;
        g.hLine(x, x + cardW - 1, y, border);
        g.hLine(x, x + cardW - 1, y + cardH - 1, border);
        g.vLine(x, y, y + cardH - 1, border);
        g.vLine(x + cardW - 1, y, y + cardH - 1, border);

        int cx = x + cardW / 2;

        // Emoji — proportionally placed
        int emojiY = y + cardH / 5;
        drawScaled(g, font, Component.literal(card.emoji()), cx, emojiY, card.color() | 0xFF000000, textScale);

        // Label (with 2-line wrap), overflow checked in scaled space
        Component label = Component.translatable(card.labelKey()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        int labelY = y + (int) (cardH * 0.42f);
        float scaledMax = (cardW - 8) / textScale;
        if (font.width(label) <= scaledMax) {
            drawScaled(g, font, label, cx, labelY, 0xFFFFFF, textScale);
        } else {
            String ls = label.getString();
            int lmid = ls.length() / 2;
            int lsplit = nearestSpace(ls, lmid);
            int lineGap = (int) (5 / textScale);
            drawScaled(g, font, Component.literal(ls.substring(0, lsplit)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), cx, labelY - lineGap, 0xFFFFFF, textScale);
            drawScaled(g, font, Component.literal(ls.substring(lsplit + 1)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), cx, labelY + lineGap, 0xFFFFFF, textScale);
        }

        // Sub-label (with 2-line wrap) — extra vertical gap from label
        Component sub = Component.translatable(card.sublabelKey()).withStyle(ChatFormatting.GRAY);
        int subY = y + (int) (cardH * 0.73f);
        if (font.width(sub) <= scaledMax) {
            drawScaled(g, font, sub, cx, subY, 0x888888, textScale);
        } else {
            String s = sub.getString();
            int mid = s.length() / 2;
            int split = nearestSpace(s, mid);
            int lineGap = (int) (4 / textScale);
            drawScaled(g, font, Component.literal(s.substring(0, split)), cx, subY - lineGap, 0x888888, textScale);
            drawScaled(g, font, Component.literal(s.substring(split + 1)), cx, subY + lineGap, 0x888888, textScale);
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
        if (button == 0 && hoveredCard >= 0) {
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
