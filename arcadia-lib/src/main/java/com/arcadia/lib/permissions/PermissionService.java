package com.arcadia.lib.permissions;

import com.arcadia.lib.DebugMode;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * Central permission API for all Arcadia mods. Wraps a pluggable {@link PermissionBackend}
 * (LuckPerms, custom, or no-op) behind a simple static facade.
 *
 * <p>Usage from any mod: {@code PermissionService.hasPermission(player, "arcadia.feature.xxx")}</p>
 *
 * <p>Initialized during {@code ServerAboutToStartEvent} by whichever mod bootstraps the server
 * (typically arcadia-prestige). Falls back to {@link PermissionBackend#NOOP} if never initialized.</p>
 */
public final class PermissionService {

    private static PermissionBackend backend = PermissionBackend.NOOP;

    private static final Map<String, Integer> GRADE_HIERARCHY = Map.of(
            "default", 0,
            "vip",     1,
            "vip+",    2,
            "mvp",     3
    );

    private PermissionService() {}

    /** Initializes with the given backend. Call once at server start. */
    public static void init(PermissionBackend impl) {
        backend = impl != null ? impl : PermissionBackend.NOOP;
    }

    /** Resets to NOOP (call on server stop). */
    public static void shutdown() {
        backend = PermissionBackend.NOOP;
    }

    // ── Permission checks ───────────────────────────────────────────────────

    /** Checks if the player has a specific permission node. */
    public static boolean hasPermission(Player player, String node) {
        if (DebugMode.ENABLED) return true;
        return backend.hasPermission(player, node);
    }

    /** Returns the player's highest gameplay grade. */
    public static String getGrade(Player player) {
        if (DebugMode.ENABLED) {
            String override = DebugMode.getDebugGrade(player.getUUID());
            return override != null ? override : "vip+";
        }
        return backend.getGrade(player);
    }

    /** Returns true if the player has the Founder cosmetic rank. */
    public static boolean isFounder(Player player) {
        if (DebugMode.ENABLED) return DebugMode.isDebugFounder(player.getUUID());
        return backend.isFounder(player);
    }

    /** Checks if the player's grade meets or exceeds the required grade. */
    public static boolean hasMinimumGrade(Player player, String requiredGrade) {
        int playerLevel = GRADE_HIERARCHY.getOrDefault(getGrade(player), 0);
        int requiredLevel = GRADE_HIERARCHY.getOrDefault(requiredGrade, 0);
        return playerLevel >= requiredLevel;
    }

    /** Returns the numeric level for a grade string. */
    public static int getGradeLevel(String grade) {
        return GRADE_HIERARCHY.getOrDefault(grade, 0);
    }

    // ── Convenience methods ─────────────────────────────────────────────────

    /** Checks a feature permission: arcadia.feature.<featureId> */
    public static boolean canAccessFeature(Player player, String featureId) {
        return hasPermission(player, "arcadia.feature." + featureId);
    }

    /** Checks a cosmetic permission: arcadia.cosmetic.<cosmeticId> */
    public static boolean canUseCosmetic(Player player, String cosmeticId) {
        return hasPermission(player, "arcadia.cosmetic." + cosmeticId);
    }

    /** Checks a hub card permission: arcadia.hub.<cardId> */
    public static boolean canSeeHubCard(Player player, String cardId) {
        return hasPermission(player, "arcadia.hub." + cardId);
    }
}
