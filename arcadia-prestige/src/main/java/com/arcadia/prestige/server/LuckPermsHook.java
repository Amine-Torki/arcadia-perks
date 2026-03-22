package com.arcadia.prestige.server;

import com.mojang.logging.LogUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Integration with the LuckPerms permission API for grade-based access control.
 * Provides grade detection, hierarchy comparison, and particle permission checks.
 */
public final class LuckPermsHook {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static LuckPerms api;

    /**
     * Gameplay rank hierarchy. Founder is NOT a gameplay rank — it is a cosmetic
     * add-on checked separately via {@link #isFounder(Player)}.
     */
    private static final Map<String, Integer> GRADE_HIERARCHY = Map.of(
            "default", 0,
            "vip",     1,
            "vip+",    2,
            "mvp",     3
    );

    private static final Map<String, String> PARTICLE_GRADE_REQUIREMENTS = Map.ofEntries(
            // Static effects
            Map.entry("orbit",  "vip"),
            Map.entry("aura",   "vip+"),
            Map.entry("wings",  "vip+"),
            Map.entry("storm",  "founder"),
            Map.entry("crown",  "founder"),
            // Movement effects
            Map.entry("trail",  "vip"),
            Map.entry("hearts", "vip"),
            Map.entry("enchant","vip+"),
            Map.entry("flame",  "vip+"),
            Map.entry("stars",  "founder")
    );

    private LuckPermsHook() {}

    /**
     * Attempts to initialize the LuckPerms API hook.
     * Fails gracefully if LuckPerms is not installed.
     */
    public static void init() {
        try {
            api = LuckPermsProvider.get();
            LOGGER.info("LuckPerms integration initialized successfully.");
        } catch (Throwable e) {
            api = null;
            LOGGER.warn("LuckPerms not available — grade features will use debug defaults. ({})", e.getClass().getSimpleName());
        }
    }

    /**
     * Returns the player's highest gameplay rank: "mvp" > "vip+" > "vip" > "default".
     * Founder is a cosmetic add-on — use {@link #isFounder(Player)} for that check.
     */
    public static String getGrade(Player player) {
        if (com.arcadia.lib.DebugMode.ENABLED) {
            String override = com.arcadia.lib.DebugMode.getDebugGrade(player.getUUID());
            return override != null ? override : "vip+"; // default debug grade
        }

        if (api == null) return "default";

        User user = api.getUserManager().getUser(player.getUUID());
        if (user == null) return "default";

        if (hasPermission(user, "arcadia.grade.mvp"))     return "mvp";
        if (hasPermission(user, "arcadia.grade.vipplus")) return "vip+";
        if (hasPermission(user, "arcadia.grade.vip"))     return "vip";
        return "default";
    }

    /**
     * Returns true if the player holds the Founder cosmetic rank.
     * Founder is independent of the gameplay rank ladder.
     */
    public static boolean isFounder(Player player) {
        if (com.arcadia.lib.DebugMode.ENABLED) {
            return com.arcadia.lib.DebugMode.isDebugFounder(player.getUUID());
        }
        if (api == null) return false;
        User user = api.getUserManager().getUser(player.getUUID());
        if (user == null) return false;
        return hasPermission(user, "arcadia.grade.founder");
    }

    /**
     * Checks whether a user has a specific permission node.
     */
    private static boolean hasPermission(User user, String permission) {
        return user.getCachedData()
                .getPermissionData(QueryOptions.defaultContextualOptions())
                .checkPermission(permission)
                .asBoolean();
    }

    /**
     * Checks whether a player's grade meets or exceeds the required grade.
     */
    public static boolean hasMinimumGrade(Player player, String requiredGrade) {
        String playerGrade = getGrade(player);
        int playerLevel = GRADE_HIERARCHY.getOrDefault(playerGrade, 0);
        int requiredLevel = GRADE_HIERARCHY.getOrDefault(requiredGrade, 0);
        return playerLevel >= requiredLevel;
    }

    /**
     * Checks whether a player's grade allows use of a specific particle effect.
     * Founder-gated particles use {@link #isFounder(Player)} instead of the gameplay rank.
     */
    public static boolean canUseParticle(Player player, String particleId) {
        String requiredGrade = PARTICLE_GRADE_REQUIREMENTS.get(particleId);
        if (requiredGrade == null) return true;
        if ("founder".equals(requiredGrade)) return isFounder(player);
        return hasMinimumGrade(player, requiredGrade);
    }

    /**
     * Validates that a player has the required grade for a particle effect.
     * Used by C2SDashboardAction to gate particle selection.
     */
    public static boolean validateGrade(Player player, String particleId) {
        return canUseParticle(player, particleId);
    }

    /**
     * Returns the required grade string for a given particle ID, or "default" if none required.
     */
    public static String getRequiredGrade(String particleId) {
        return PARTICLE_GRADE_REQUIREMENTS.getOrDefault(particleId, "default");
    }

    /**
     * Returns the grade hierarchy level for display purposes.
     */
    public static int getGradeLevel(String grade) {
        return GRADE_HIERARCHY.getOrDefault(grade, 0);
    }
}
