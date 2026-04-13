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

    // ── Broadcast utilities (from ArcadiaCore MessageUtil) ──────────────────

    /** Sends a themed message to a player. Supports legacy &-color codes. */
    public static void send(net.minecraft.server.level.ServerPlayer player, String message) {
        if (player == null || message == null) return;
        player.sendSystemMessage(com.arcadia.lib.text.LegacyColorFormatter.parse(message));
    }

    /** Broadcasts a message to all online players. */
    public static void broadcastAll(net.minecraft.server.MinecraftServer server, String message) {
        if (server == null || message == null) return;
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, message);
        }
    }

    /** Broadcasts a message to specific players by UUID. */
    public static void broadcast(net.minecraft.server.MinecraftServer server,
                                 java.util.Collection<java.util.UUID> playerIds, String message) {
        if (server == null || playerIds == null || message == null) return;
        for (java.util.UUID id : playerIds) {
            if (id == null) continue;
            net.minecraft.server.level.ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) send(p, message);
        }
    }
}
