package com.arcadia.lib.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Simplified server-side sound playback for all Arcadia mods.
 * Replaces verbose 7-argument playSound calls with 2-argument helpers.
 */
public final class SoundHelper {

    private SoundHelper() {}

    // ── Common Arcadia sounds ───────────────────────────────────────────────

    public static final SoundEvent SUCCESS  = SoundEvents.PLAYER_LEVELUP;
    public static final SoundEvent ERROR    = SoundEvents.VILLAGER_NO;
    public static final SoundEvent CLICK    = SoundEvents.UI_BUTTON_CLICK.value();
    public static final SoundEvent REWARD   = SoundEvents.EXPERIENCE_ORB_PICKUP;
    public static final SoundEvent TELEPORT = SoundEvents.ENDERMAN_TELEPORT;

    // ── Play at player ──────────────────────────────────────────────────────

    /** Plays a sound at the player's location with default volume/pitch. */
    public static void playAt(ServerPlayer player, SoundEvent sound) {
        playAt(player, sound, 1.0f, 1.0f);
    }

    /** Plays a sound at the player's location with custom volume and pitch. */
    public static void playAt(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ── Play at position ────────────────────────────────────────────────────

    /** Plays a sound at a specific position in a level. */
    public static void playAt(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ── Convenience ─────────────────────────────────────────────────────────

    /** Plays the success sound for a player. */
    public static void success(ServerPlayer player) { playAt(player, SUCCESS, 0.5f, 1.2f); }

    /** Plays the error sound for a player. */
    public static void error(ServerPlayer player) { playAt(player, ERROR, 0.5f, 1.0f); }

    /** Plays the reward sound for a player. */
    public static void reward(ServerPlayer player) { playAt(player, REWARD, 0.7f, 1.0f); }
}
