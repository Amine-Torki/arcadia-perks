package com.arcadia.lib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Reusable steampunk/copper-themed rendering utilities for all Arcadia mod GUIs.
 * Provides a consistent visual identity across modules: warm copper tones,
 * riveted borders, gradient panels, and properly centered text.
 *
 * <p>All methods are static. Colors follow Minecraft's ARGB format (0xAARRGGBB).</p>
 */
public final class ArcadiaTheme {

    private ArcadiaTheme() {}

    // ── Core palette ────────────────────────────────────────────────────────

    /** Copper primary accent. */
    public static final int COPPER        = 0xFFB87333;
    /** Darker bronze for secondary elements. */
    public static final int BRONZE        = 0xFFCD7F32;
    /** Warm brass for highlights. */
    public static final int BRASS         = 0xFFD4A847;
    /** Amber glow for active/hovered elements. */
    public static final int AMBER         = 0xFFFFBF00;
    /** Patina green — oxidized copper accent. */
    public static final int PATINA        = 0xFF4ECCA3;

    /** Warm cream text (replaces pure white). */
    public static final int TEXT_PRIMARY  = 0xFFF5E6C8;
    /** Muted warm text for secondary info. */
    public static final int TEXT_SECONDARY = 0xFFB8A88A;
    /** Subtle dim text for hints. */
    public static final int TEXT_DIM      = 0xFF7A6E5A;

    /** Dark steel panel background (top). */
    public static final int PANEL_BG_TOP  = 0xEE1A1620;
    /** Darker bottom for gradient. */
    public static final int PANEL_BG_BOT  = 0xEE0E0B12;

    /** Panel border (idle). */
    public static final int BORDER_IDLE   = 0xFF3A3028;
    /** Panel border (hovered) — warm copper. */
    public static final int BORDER_HOVER  = COPPER;

    /** Full-screen overlay background. */
    public static final int OVERLAY_BG    = 0xBB0A0810;

    // ── Panel rendering ─────────────────────────────────────────────────────

    /**
     * Draws a themed panel with gradient background, copper borders, and riveted corners.
     */
    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        int accent = hovered ? AMBER : COPPER;

        // Drop shadow
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x55000000);

        // Gradient background
        g.fill(x, y, x + w, y + h / 2, PANEL_BG_TOP);
        g.fill(x, y + h / 2, x + w, y + h, PANEL_BG_BOT);

        // Top accent bar (copper/amber)
        g.fill(x, y, x + w, y + 2, accent);

        // Inner highlight below accent
        g.fill(x + 1, y + 2, x + w - 1, y + 3, hovered ? 0x33FFFFFF : 0x15FFFFFF);

        // Border
        int border = hovered ? accent : BORDER_IDLE;
        drawBorder(g, x, y, w, h, border);

        // Corner rivets (small bright dots at each corner)
        int rivet = hovered ? BRASS : 0xFF504030;
        g.fill(x + 1, y + 1, x + 3, y + 3, rivet);
        g.fill(x + w - 3, y + 1, x + w - 1, y + 3, rivet);
        g.fill(x + 1, y + h - 3, x + 3, y + h - 1, rivet);
        g.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, rivet);
    }

    /**
     * Draws a themed panel with a custom accent color override.
     */
    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h,
                                 boolean hovered, int accentColor) {
        int accent = hovered ? brighten(accentColor, 40) : accentColor;

        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x55000000);
        g.fill(x, y, x + w, y + h / 2, PANEL_BG_TOP);
        g.fill(x, y + h / 2, x + w, y + h, PANEL_BG_BOT);
        g.fill(x, y, x + w, y + 2, accent);
        g.fill(x + 1, y + 2, x + w - 1, y + 3, hovered ? 0x33FFFFFF : 0x15FFFFFF);
        drawBorder(g, x, y, w, h, hovered ? accent : darken(accentColor, 60));

        int rivet = hovered ? BRASS : 0xFF504030;
        g.fill(x + 1, y + 1, x + 3, y + 3, rivet);
        g.fill(x + w - 3, y + 1, x + w - 1, y + 3, rivet);
        g.fill(x + 1, y + h - 3, x + 3, y + h - 1, rivet);
        g.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, rivet);
    }

    // ── Border / separator ──────────────────────────────────────────────────

    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.hLine(x, x + w - 1, y, color);
        g.hLine(x, x + w - 1, y + h - 1, color);
        g.vLine(x, y, y + h - 1, color);
        g.vLine(x + w - 1, y, y + h - 1, color);
    }

    /** Horizontal separator line with fade-out edges. */
    public static void drawSeparator(GuiGraphics g, int x, int y, int w, int color) {
        int pad = w / 6;
        g.fill(x + pad, y, x + w - pad, y + 1, color);
    }

    // ── Glow effects ────────────────────────────────────────────────────────

    /** Draws a colored outer glow around a rectangle. */
    public static void drawGlow(GuiGraphics g, int x, int y, int w, int h, int color) {
        int glow1 = (color & 0x00FFFFFF) | 0x18000000;
        int glow2 = (color & 0x00FFFFFF) | 0x28000000;
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, glow1);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, glow2);
    }

    // ── Text rendering ──────────────────────────────────────────────────────

    /** Draws centered text with drop shadow at the given position. */
    public static void drawCenteredText(GuiGraphics g, Component text, int cx, int y, int color) {
        Font font = Minecraft.getInstance().font;
        // Shadow
        g.drawCenteredString(font, text, cx + 1, y + 1, 0x22000000);
        g.drawCenteredString(font, text, cx, y, color);
    }

    /** Draws centered scaled text at (cx, y). Handles PoseStack transform. */
    public static void drawCenteredScaled(GuiGraphics g, Component text, int cx, int y,
                                          int color, float scale) {
        Font font = Minecraft.getInstance().font;
        if (scale == 1.0f) {
            drawCenteredText(g, text, cx, y, color);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(font, text, 0, 0, color);
        g.pose().popPose();
    }

    /** Draws left-aligned text at (x, y) with shadow. */
    public static void drawText(GuiGraphics g, Component text, int x, int y, int color) {
        Font font = Minecraft.getInstance().font;
        g.drawString(font, text, x + 1, y + 1, 0x22000000, false);
        g.drawString(font, text, x, y, color, false);
    }

    // ── Title bar ───────────────────────────────────────────────────────────

    /**
     * Draws a decorated title bar: centered text with decorative copper lines on both sides.
     */
    public static void drawTitleBar(GuiGraphics g, Component title, int cx, int y, int lineW) {
        Font font = Minecraft.getInstance().font;
        int textW = font.width(title);
        int lineStart = cx - lineW / 2;
        int lineEnd = cx + lineW / 2;
        int textLeft = cx - textW / 2;
        int textRight = cx + textW / 2;

        // Copper lines on each side of title
        g.fill(lineStart, y + 4, textLeft - 6, y + 5, COPPER);
        g.fill(textRight + 6, y + 4, lineEnd, y + 5, COPPER);

        // Small diamond decorations at ends
        g.fill(lineStart - 1, y + 3, lineStart + 1, y + 6, BRASS);
        g.fill(lineEnd - 1, y + 3, lineEnd + 1, y + 6, BRASS);

        // Title text with shadow
        g.drawCenteredString(font, title, cx + 1, y + 1, 0x33000000);
        g.drawCenteredString(font, title, cx, y, BRASS);
    }

    // ── Color utilities ─────────────────────────────────────────────────────

    /** Brightens an ARGB color by the given amount (0-255). */
    public static int brighten(int argb, int amount) {
        int a = (argb >> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + amount);
        int b = Math.min(255, (argb & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Darkens an ARGB color by the given amount (0-255). */
    public static int darken(int argb, int amount) {
        int a = (argb >> 24) & 0xFF;
        int r = Math.max(0, ((argb >> 16) & 0xFF) - amount);
        int g = Math.max(0, ((argb >> 8) & 0xFF) - amount);
        int b = Math.max(0, (argb & 0xFF) - amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Sets the alpha of an ARGB color. */
    public static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}
