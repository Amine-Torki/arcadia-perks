package com.arcadia.lib.permissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

public final class PermissionUtil {
    private PermissionUtil() {}

    public static boolean commandSourceHasOpLevel(CommandSourceStack source, int opLevel) {
        return source != null && source.hasPermission(opLevel);
    }

    public static boolean playerHasPermissionOrOp(ServerPlayer player, PermissionNode<Boolean> node, int fallbackOpLevel) {
        if (player == null) return false;
        if (player.hasPermissions(fallbackOpLevel)) return true;

        try {
            return PermissionAPI.getPermission(player, node);
        } catch (Exception ignored) {
            return false;
        }
    }
}
