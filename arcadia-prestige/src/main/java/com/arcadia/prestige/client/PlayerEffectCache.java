package com.arcadia.prestige.client;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of active particle effects for all visible players.
 * Thread-safe via ConcurrentHashMap since network packets may arrive off the render thread.
 */
public final class PlayerEffectCache {

    private static final Map<UUID, String> activeEffects = new ConcurrentHashMap<>();

    private PlayerEffectCache() {
    }

    /**
     * Updates the effect for a player. If particleId is empty or null, the entry is removed.
     */
    public static void update(UUID playerUuid, String particleId) {
        if (particleId == null || particleId.isEmpty()) {
            activeEffects.remove(playerUuid);
        } else {
            activeEffects.put(playerUuid, particleId);
        }
    }

    /**
     * Returns the active effect ID for the given player, or null if none.
     */
    public static String getEffect(UUID uuid) {
        return activeEffects.get(uuid);
    }

    /**
     * Returns an unmodifiable view of all active effects.
     */
    public static Map<UUID, String> getAll() {
        return Collections.unmodifiableMap(activeEffects);
    }

    /**
     * Clears all cached effects. Call on disconnect.
     */
    public static void clear() {
        activeEffects.clear();
    }

    // -------------------------------------------------------------------------
    // First-person hide preference (client-only, not synced to server)
    // -------------------------------------------------------------------------

    private static volatile boolean hideOwnEffectsFirstPerson = true;

    public static boolean isHideOwnEffectsFirstPerson() {
        return hideOwnEffectsFirstPerson;
    }

    public static void toggleHideOwnEffectsFirstPerson() {
        hideOwnEffectsFirstPerson = !hideOwnEffectsFirstPerson;
    }
}
