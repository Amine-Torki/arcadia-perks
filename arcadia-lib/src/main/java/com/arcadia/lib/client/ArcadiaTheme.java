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

    // ── Container GUI rendering ───────────────────────────────────────────

    /** Slot background color (dark iron). */
    public static final int SLOT_BG       = 0xFF1A1620;
    /** Slot border light edge (top/left). */
    public static final int SLOT_LIGHT    = 0xFF3A3430;
    /** Slot border dark edge (bottom/right). */
    public static final int SLOT_DARK     = 0xFF0C0A0E;

    /**
     * Draws a single 18×18 item slot with beveled iron edges at the given position.
     * The (x,y) is the slot's top-left corner (same as Minecraft's Slot.x, Slot.y minus 1).
     */
    public static void drawSlot(GuiGraphics g, int x, int y) {
        // Outer bevel (top-left = light, bottom-right = dark)
        g.hLine(x, x + 17, y, SLOT_LIGHT);
        g.vLine(x, y, y + 17, SLOT_LIGHT);
        g.hLine(x, x + 17, y + 17, SLOT_DARK);
        g.vLine(x + 17, y, y + 17, SLOT_DARK);
        // Inner fill
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BG);
    }

    /**
     * Draws a grid of item slots (rows × 9 columns) starting at (x, y).
     * Slots are 18×18 with no gap (standard Minecraft layout).
     */
    public static void drawSlotGrid(GuiGraphics g, int x, int y, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, x + col * 18, y + row * 18);
            }
        }
    }

    /**
     * Draws a full container background (replaces the chest texture):
     * header with title area, content slots, player inventory, and hotbar.
     * Positions match standard Minecraft slot layout: content at y+18, inv at y+140, hotbar at y+198.
     *
     * @param x          left edge
     * @param y          top edge
     * @param w          width (176 standard)
     * @param contentRows number of content slot rows (typically 6 for a double chest)
     */
    public static void drawContainerBg(GuiGraphics g, int x, int y, int w, int contentRows) {
        // Matches Minecraft's standard slot positions:
        // Content: (8 + col*18, 18 + row*18) → bg starts at (7, 17)
        // Player inv: (8 + col*18, 140 + row*18) → bg starts at (7, 139)
        // Hotbar: (8 + col*18, 198) → bg starts at (7, 197)
        int totalH = 222; // Standard 6-row container height

        // Drop shadow
        g.fill(x + 3, y + 3, x + w + 3, y + totalH + 3, 0x55000000);

        // Main background gradient
        g.fill(x, y, x + w, y + totalH / 2, 0xF01E1A24);
        g.fill(x, y + totalH / 2, x + w, y + totalH, 0xF0141018);

        // Outer border (copper)
        drawBorder(g, x, y, w, totalH, BORDER_IDLE);

        // Top accent bar
        g.fill(x, y, x + w, y + 2, COPPER);

        // Corner rivets
        int rivet = 0xFF504030;
        g.fill(x + 1, y + 1, x + 3, y + 3, rivet);
        g.fill(x + w - 3, y + 1, x + w - 1, y + 3, rivet);
        g.fill(x + 1, y + totalH - 3, x + 3, y + totalH - 1, rivet);
        g.fill(x + w - 3, y + totalH - 3, x + w - 1, y + totalH - 1, rivet);

        // Header inner highlight
        g.fill(x + 1, y + 2, x + w - 1, y + 3, 0x18FFFFFF);

        int slotX = x + 7;

        // Content slot area (6 rows starting at y+17)
        drawSlotGrid(g, slotX, y + 17, contentRows);

        // Separator between content and player inventory
        int sepY = y + 17 + contentRows * 18 + 3;
        g.fill(x + 7, sepY, x + w - 7, sepY + 1, withAlpha(COPPER, 0x33));

        // Player inventory label area highlight
        g.fill(x + 6, y + 127, x + w - 6, y + 128, 0x0CFFFFFF);

        // Player inventory slots (3 rows starting at y+139)
        drawSlotGrid(g, slotX, y + 139, 3);

        // Hotbar (1 row starting at y+197)
        drawSlotGrid(g, slotX, y + 197, 1);

        // Bottom accent bar
        g.fill(x, y + totalH - 2, x + w, y + totalH, darken(COPPER, 40));
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
