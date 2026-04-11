package com.arcadia.lib.player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic per-player cooldown tracker for any Arcadia mod action.
 * Stores cooldown end timestamps by (player UUID + action ID).
 *
 * <p>Usage:
 * <pre>
 * if (CooldownManager.isReady(player.getUUID(), "fishing.cast")) {
 *     CooldownManager.set(player.getUUID(), "fishing.cast", 5000); // 5 second cooldown
 *     // perform action
 * }
 * </pre></p>
 */
public final class CooldownManager {

    /** Key: "uuid:actionId" → Value: System.currentTimeMillis() when cooldown expires. */
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    private CooldownManager() {}

    /**
     * Sets a cooldown for a player action.
     * @param uuid     player UUID
     * @param actionId action identifier (e.g. "fishing.cast", "teleport.home")
     * @param durationMs cooldown duration in milliseconds
     */
    public static void set(UUID uuid, String actionId, long durationMs) {
        cooldowns.put(key(uuid, actionId), System.currentTimeMillis() + durationMs);
    }

    /**
     * Checks if the cooldown has expired (player is ready to act).
     * Returns true if no cooldown is set or if it has expired.
     */
    public static boolean isReady(UUID uuid, String actionId) {
        Long end = cooldowns.get(key(uuid, actionId));
        return end == null || System.currentTimeMillis() >= end;
    }

    /**
     * Returns remaining cooldown time in milliseconds, or 0 if ready.
     */
    public static long getRemaining(UUID uuid, String actionId) {
        Long end = cooldowns.get(key(uuid, actionId));
        if (end == null) return 0;
        return Math.max(0, end - System.currentTimeMillis());
    }

    /**
     * Returns remaining cooldown as a formatted string (e.g. "2m 30s"), or empty if ready.
     */
    public static String getRemainingFormatted(UUID uuid, String actionId) {
        long remaining = getRemaining(uuid, actionId);
        if (remaining <= 0) return "";
        return com.arcadia.lib.text.TextFormatter.formatMs(remaining);
    }

    /** Clears all cooldowns for a player (called on disconnect). */
    public static void clearPlayer(UUID uuid) {
        String prefix = uuid.toString() + ":";
        cooldowns.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
    }

    /** Clears a specific cooldown. */
    public static void clear(UUID uuid, String actionId) {
        cooldowns.remove(key(uuid, actionId));
    }

    private static String key(UUID uuid, String actionId) {
        return uuid.toString() + ":" + actionId;
    }
}
