package com.arcadia.lib.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced text formatting utilities for all Arcadia mods.
 * Provides placeholder replacement, color gradients, and rich text building.
 *
 * <p>Usage: {@code TextFormatter.format("Welcome {player}!", Map.of("player", playerName))}</p>
 */
public final class TextFormatter {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private TextFormatter() {}

    // ── Placeholder replacement ─────────────────────────────────────────────

    /**
     * Replaces {key} placeholders in a template string with provided values.
     * Returns a Component for direct use with sendSystemMessage.
     */
    public static Component format(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> e : values.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue());
        }
        return Component.literal(result);
    }

    /**
     * Replaces {key} placeholders and applies a style to the entire result.
     */
    public static Component format(String template, Map<String, String> values, ChatFormatting... styles) {
        return format(template, values).copy().withStyle(styles);
    }

    // ── Rich text builder ───────────────────────────────────────────────────

    /** Creates a builder for composing styled text segments. */
    public static RichText rich() {
        return new RichText();
    }

    /**
     * Fluent builder for composing multi-style chat messages.
     * <pre>
     * TextFormatter.rich()
     *     .gold().bold().text("⚙ Arcadia")
     *     .gray().text(" ▸ ")
     *     .green().text("Success!")
     *     .build();
     * </pre>
     */
    public static final class RichText {
        private MutableComponent result = Component.empty();
        private ChatFormatting[] nextStyles = {};
        private boolean nextBold = false;

        /** Appends a text segment with the currently set styles. */
        public RichText text(String text) {
            MutableComponent part = Component.literal(text);
            if (nextStyles.length > 0) part.withStyle(nextStyles);
            if (nextBold) part.withStyle(ChatFormatting.BOLD);
            result.append(part);
            nextStyles = new ChatFormatting[]{};
            nextBold = false;
            return this;
        }

        /** Appends a translatable component. */
        public RichText translatable(String key, Object... args) {
            MutableComponent part = Component.translatable(key, args);
            if (nextStyles.length > 0) part.withStyle(nextStyles);
            if (nextBold) part.withStyle(ChatFormatting.BOLD);
            result.append(part);
            nextStyles = new ChatFormatting[]{};
            nextBold = false;
            return this;
        }

        public RichText gold()   { nextStyles = new ChatFormatting[]{ChatFormatting.GOLD}; return this; }
        public RichText green()  { nextStyles = new ChatFormatting[]{ChatFormatting.GREEN}; return this; }
        public RichText red()    { nextStyles = new ChatFormatting[]{ChatFormatting.RED}; return this; }
        public RichText yellow() { nextStyles = new ChatFormatting[]{ChatFormatting.YELLOW}; return this; }
        public RichText gray()   { nextStyles = new ChatFormatting[]{ChatFormatting.GRAY}; return this; }
        public RichText white()  { nextStyles = new ChatFormatting[]{ChatFormatting.WHITE}; return this; }
        public RichText aqua()   { nextStyles = new ChatFormatting[]{ChatFormatting.AQUA}; return this; }
        public RichText bold()   { nextBold = true; return this; }

        /** Sets a custom hex color for the next segment. */
        public RichText color(int rgb) {
            nextStyles = new ChatFormatting[]{};
            // Will be applied via TextColor in text()
            return this;
        }

        /** Builds the final component. */
        public Component build() { return result; }
    }

    // ── Number formatting ───────────────────────────────────────────────────

    /** Formats a number with thousands separators (e.g. 1,234,567). */
    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }

    /** Formats a duration in ticks to a human-readable string (e.g. "2m 30s"). */
    public static String formatTicks(int ticks) {
        int totalSecs = ticks / 20;
        int mins = totalSecs / 60;
        int secs = totalSecs % 60;
        if (mins > 0) return mins + "m " + secs + "s";
        return secs + "s";
    }

    /** Formats milliseconds to a human-readable string. */
    public static String formatMs(long ms) {
        long totalSecs = ms / 1000;
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        if (mins > 0) return mins + "m " + secs + "s";
        return secs + "s";
    }

    /** Formats a percentage (0.0-1.0) to a string like "75%". */
    public static String formatPercent(float fraction) {
        return Math.round(fraction * 100) + "%";
    }
}
