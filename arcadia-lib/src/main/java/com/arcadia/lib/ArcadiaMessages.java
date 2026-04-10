package com.arcadia.lib;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Themed chat message builder for all Arcadia mods.
 * Provides consistent copper/steampunk styling for player-facing messages.
 */
public final class ArcadiaMessages {

    private ArcadiaMessages() {}

    /** Arcadia prefix: "⚙ Arcadia ▸ " in copper/gold tones. */
    private static MutableComponent prefix() {
        return Component.literal("\u2699 ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Arcadia")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" \u25B8 ")
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Success message (green text after prefix). */
    public static Component success(String message) {
        return prefix().append(Component.literal(message).withStyle(ChatFormatting.GREEN));
    }

    /** Success with translatable key. */
    public static Component success(Component message) {
        return prefix().append(message.copy().withStyle(ChatFormatting.GREEN));
    }

    /** Error message (red text after prefix). */
    public static Component error(String message) {
        return prefix().append(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    /** Error with translatable key. */
    public static Component error(Component message) {
        return prefix().append(message.copy().withStyle(ChatFormatting.RED));
    }

    /** Info message (gray text after prefix). */
    public static Component info(String message) {
        return prefix().append(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }

    /** Info with translatable key. */
    public static Component info(Component message) {
        return prefix().append(message.copy().withStyle(ChatFormatting.GRAY));
    }

    /** Warning message (yellow text after prefix). */
    public static Component warning(String message) {
        return prefix().append(Component.literal(message).withStyle(ChatFormatting.YELLOW));
    }

    /** Highlight (white bold) a value within a message. */
    public static Component highlight(String value) {
        return Component.literal(value).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
    }
}
