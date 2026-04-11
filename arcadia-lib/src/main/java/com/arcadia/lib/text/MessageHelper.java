package com.arcadia.lib.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends action bar messages, titles, and subtitles to players.
 * Wraps the verbose packet construction into simple one-liners.
 */
public final class MessageHelper {

    private MessageHelper() {}

    // ── Action bar ──────────────────────────────────────────────────────────

    /** Sends a text message to the player's action bar (above hotbar). */
    public static void sendActionBar(ServerPlayer player, Component message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }

    /** Sends a string to the action bar. */
    public static void sendActionBar(ServerPlayer player, String message) {
        sendActionBar(player, Component.literal(message));
    }

    // ── Titles ──────────────────────────────────────────────────────────────

    /**
     * Sends a title + subtitle with custom animation timings.
     * @param fadeIn  fade-in duration in ticks
     * @param stay    display duration in ticks
     * @param fadeOut fade-out duration in ticks
     */
    public static void sendTitle(ServerPlayer player, Component title, Component subtitle,
                                 int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        if (subtitle != null) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    /** Sends a title with default timings (10 fade-in, 40 stay, 10 fade-out). */
    public static void sendTitle(ServerPlayer player, Component title) {
        sendTitle(player, title, null, 10, 40, 10);
    }

    /** Sends a title + subtitle with default timings. */
    public static void sendTitle(ServerPlayer player, Component title, Component subtitle) {
        sendTitle(player, title, subtitle, 10, 40, 10);
    }

    /** Sends only a subtitle (clears any existing title). */
    public static void sendSubtitle(ServerPlayer player, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }
}
