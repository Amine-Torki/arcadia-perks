package com.arcadia.lib.staff;

import com.arcadia.lib.DebugMode;
import com.arcadia.lib.permissions.PermissionService;
import com.arcadia.lib.player.PlayerManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Central staff/moderation API for all Arcadia mods.
 * Determines staff roles via LuckPerms permission nodes.
 *
 * <p>Usage: {@code StaffService.getRole(player).atLeast(StaffRole.MOD)}</p>
 */
public final class StaffService {

    private StaffService() {}

    /** Returns the highest staff role for this player. */
    public static StaffRole getRole(Player player) {
        if (DebugMode.ENABLED && DebugMode.isDebugPlayer(player)) return StaffRole.ADMIN;
        if (PermissionService.hasPermission(player, StaffConfig.NODE_ADMIN))  return StaffRole.ADMIN;
        if (PermissionService.hasPermission(player, StaffConfig.NODE_MOD))    return StaffRole.MOD;
        if (PermissionService.hasPermission(player, StaffConfig.NODE_HELPER)) return StaffRole.HELPER;
        return StaffRole.NONE;
    }

    /** Shorthand: returns true if the player has any staff role. */
    public static boolean isStaff(Player player) {
        return getRole(player).isStaff();
    }

    /**
     * Command guard: checks if the source has at least the required role.
     * Sends an error message if not. Returns true if authorized.
     */
    public static boolean requireRole(CommandSourceStack source, StaffRole required) {
        if (!(source.getEntity() instanceof ServerPlayer sp)) {
            return source.hasPermission(2); // console always passes
        }
        if (getRole(sp).atLeast(required)) return true;
        source.sendFailure(com.arcadia.lib.ArcadiaMessages.error(
                "You need " + required.getDisplayName() + " role or higher."));
        return false;
    }

    /** Returns all online players who have any staff role. */
    public static List<ServerPlayer> getStaffOnline() {
        return PlayerManager.getOnlinePlayers().stream()
                .filter(StaffService::isStaff)
                .toList();
    }
}
