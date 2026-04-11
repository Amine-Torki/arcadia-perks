package com.arcadia.lib.client;

import com.arcadia.lib.ArcadiaModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Arcadia Hub — a full-screen menu that dynamically displays all registered
 * Arcadia modules as steampunk-themed cards. Lives in arcadia-lib so any mod
 * can open it via {@link #open()}. Modules register their cards via
 * {@link ArcadiaModRegistry#registerCard}.
 *
 * <p>Cards for uninstalled modules are shown grayed out. The hub automatically
 * adapts its layout to the number of registered cards.</p>
 */
public class ArcadiaHubScreen extends Screen {

    private static final int CARD_W = 92;
    private static final int CARD_H = 104;
    private static final int CARD_GAP = 14;

    private int hoveredCard = -1;
    private List<ArcadiaModCard> cards;

    public ArcadiaHubScreen() {
        super(Component.translatable("arcadia_lib.hub.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new ArcadiaHubScreen());
    }

    @Override
    protected void init() {
        super.init();
        cards = ArcadiaModRegistry.getCards();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        // Dark overlay
        g.fill(0, 0, this.width, this.height, ArcadiaTheme.OVERLAY_BG);

        int cardCount = cards.size();
        if (cardCount == 0) {
            ArcadiaTheme.drawCenteredText(g, Component.translatable("arcadia_lib.hub.no_modules"),
                    this.width / 2, this.height / 2, ArcadiaTheme.TEXT_DIM);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int totalW = cardCount * CARD_W + (cardCount - 1) * CARD_GAP;
        int startX = (this.width - totalW) / 2;
        int startY = (this.height - CARD_H) / 2 - 10;
        int cx = this.width / 2;

        // Title bar with copper decorations
        Component title = Component.translatable("arcadia_lib.hub.title");
        ArcadiaTheme.drawTitleBar(g, title, cx, startY - 30, totalW + 40);

        // Subtitle
        Component sub = Component.translatable("arcadia_lib.hub.subtitle");
        g.drawCenteredString(this.font, sub, cx, startY - 16, ArcadiaTheme.TEXT_SECONDARY);

        // Separator
        ArcadiaTheme.drawSeparator(g, cx - totalW / 2 - 20, startY - 6,
                totalW + 40, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x44));

        // Cards
        hoveredCard = -1;
        for (int i = 0; i < cardCount; i++) {
            ArcadiaModCard card = cards.get(i);
            int cardX = startX + i * (CARD_W + CARD_GAP);
            boolean hovered = card.available()
                    && mouseX >= cardX && mouseX < cardX + CARD_W
                    && mouseY >= startY && mouseY < startY + CARD_H;
            if (hovered) hoveredCard = i;
            drawCard(g, card, cardX, startY, CARD_W, CARD_H, hovered,
                    card.available() ? 1.0f : 0.35f);
            if (!card.available()) {
                g.drawCenteredString(this.font,
                        Component.translatable("arcadia_lib.hub.not_installed"),
                        cardX + CARD_W / 2, startY + CARD_H - 14, 0xFF554444);
            }
        }

        // Bottom separator
        ArcadiaTheme.drawSeparator(g, cx - totalW / 2 - 20, startY + CARD_H + 10,
                totalW + 40, ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x33));

        // Hint
        Component hint = Component.translatable("arcadia_lib.hub.close");
        g.drawCenteredString(this.font, hint, cx, startY + CARD_H + 20, ArcadiaTheme.TEXT_DIM);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * Steampunk-themed card renderer.
     */
    public static void drawCard(GuiGraphics g, ArcadiaModCard card, int x, int y,
                                int cardW, int cardH, boolean hovered, float textScale) {
        var font = Minecraft.getInstance().font;
        int accent = card.color() | 0xFF000000;

        if (hovered) {
            ArcadiaTheme.drawGlow(g, x, y, cardW, cardH, accent);
        }

        ArcadiaTheme.drawPanel(g, x, y, cardW, cardH, hovered, accent);

        int cx = x + cardW / 2;

        // Emoji
        int emojiY = y + (int)(cardH * 0.18);
        float emojiScale = textScale * (hovered ? 1.4f : 1.2f);
        drawScaled(g, font, Component.literal(card.emoji()), cx, emojiY, accent, emojiScale);

        // Separator
        int sepY = y + (int)(cardH * 0.34);
        int sepPad = cardW / 5;
        g.fill(x + sepPad, sepY, x + cardW - sepPad, sepY + 1,
                hovered ? ArcadiaTheme.withAlpha(accent, 0x88) : ArcadiaTheme.withAlpha(ArcadiaTheme.COPPER, 0x33));

        // Label
        Component label = Component.literal(Component.translatable(card.labelKey()).getString());
        int labelY = y + (int)(cardH * 0.42);
        float scaledMax = (cardW - 10) / textScale;
        int labelColor = hovered ? ArcadiaTheme.TEXT_PRIMARY : ArcadiaTheme.darken(ArcadiaTheme.TEXT_PRIMARY, 30);

        if (font.width(label) <= scaledMax) {
            drawScaled(g, font, label, cx, labelY, labelColor, textScale);
        } else {
            String ls = label.getString();
            int split = nearestSpace(ls, ls.length() / 2);
            int gap = (int)(6 / textScale);
            drawScaled(g, font, Component.literal(ls.substring(0, split).trim()), cx, labelY - gap, labelColor, textScale);
            drawScaled(g, font, Component.literal(ls.substring(split).trim()), cx, labelY + gap, labelColor, textScale);
        }

        // Sub-label
        Component subText = Component.literal(Component.translatable(card.sublabelKey()).getString());
        int subY = y + (int)(cardH * 0.70);
        int subColor = hovered ? ArcadiaTheme.TEXT_SECONDARY : ArcadiaTheme.TEXT_DIM;
        float subScale = textScale * 0.9f;

        if (font.width(subText) * subScale <= cardW - 10) {
            drawScaled(g, font, subText, cx, subY, subColor, subScale);
        } else {
            String s = subText.getString();
            int split = nearestSpace(s, s.length() / 2);
            int gap = (int)(5 / textScale);
            drawScaled(g, font, Component.literal(s.substring(0, split).trim()), cx, subY - gap, subColor, subScale);
            drawScaled(g, font, Component.literal(s.substring(split).trim()), cx, subY + gap, subColor, subScale);
        }
    }

    private static int nearestSpace(String s, int mid) {
        int before = s.lastIndexOf(' ', mid);
        int after  = s.indexOf(' ', mid + 1);
        if (before < 0 && after < 0) return mid;
        if (before < 0) return after;
        if (after  < 0) return before;
        return (mid - before <= after - mid) ? before : after;
    }

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
            ArcadiaModCard card = cards.get(hoveredCard);
            if (card.available()) {
                ArcadiaModRegistry.openTabClient(card.sortOrder());
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void renderBlurredBackground(float partialTick) { /* no blur */ }
}
