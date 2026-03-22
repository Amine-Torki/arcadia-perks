package com.arcadia.lib;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debug configuration for local/singleplayer testing without LuckPerms or MySQL.
 *
 * Set ENABLED = false before deploying to production.
 * While enabled, the database is replaced by an in-memory store — no MySQL
 * setup required. Use /pets rank <grade> to simulate different rank tiers.
 */
public final class DebugMode {

    /** Master switch — set to false for production. */
    public static final boolean ENABLED = false;

    /** Players allowed to run debug commands. */
    private static final Set<String> DEBUG_PLAYERS = Set.of("SiriusT", "Dev");

    /**
     * Per-player simulated grade override set via /pets rank.
     * "default" = no rank, "vip", "vip+", "mvp". Null entry = use default (vip+).
     * "founder" flag is tracked separately in {@link #debugFounder}.
     */
    private static final Map<UUID, String> debugGrades  = new ConcurrentHashMap<>();
    private static final Set<UUID>         debugFounder = ConcurrentHashMap.newKeySet();

    private DebugMode() {}

    /** Returns true if debug mode is active AND this player is a debug player. */
    public static boolean isDebugPlayer(Player player) {
        return ENABLED && DEBUG_PLAYERS.contains(player.getName().getString());
    }

    /** Returns true if the command source is a debug player. */
    public static boolean isDebugSource(CommandSourceStack source) {
        try {
            return ENABLED && DEBUG_PLAYERS.contains(source.getPlayerOrException().getName().getString());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the debug-overridden gameplay grade for this player, or {@code null}
     * if no override is set (callers should fall back to their own default).
     */
    public static String getDebugGrade(UUID uuid) {
        return debugGrades.get(uuid);
    }

    /** Returns whether the player has the debug founder flag set. */
    public static boolean isDebugFounder(UUID uuid) {
        return debugFounder.contains(uuid);
    }

    /**
     * Sets the simulated grade for a player. Pass {@code null} to clear.
     * Use "founder" to toggle the cosmetic founder flag (does not affect gameplay grade).
     */
    public static void setDebugGrade(UUID uuid, String grade) {
        if (grade == null || grade.equals("default") || grade.equals("none")) {
            debugGrades.put(uuid, "default");
            debugFounder.remove(uuid);
        } else if (grade.equals("founder")) {
            debugFounder.add(uuid);
        } else {
            debugGrades.put(uuid, grade);
        }
    }
}
