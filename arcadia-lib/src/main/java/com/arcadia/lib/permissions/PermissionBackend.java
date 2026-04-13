package com.arcadia.lib.permissions;

import net.minecraft.world.entity.player.Player;

/**
 * Pluggable permission backend interface. The lib ships with a no-op fallback.
 * When LuckPerms is present, {@link LuckPermsBackend} is used instead.
 *
 * <p>Custom permission plugins can implement this interface and register
 * via {@link PermissionService#init(PermissionBackend)}.</p>
 */
public interface PermissionBackend {

    /** Checks if the player has the given permission node. */
    boolean hasPermission(Player player, String node);

    /** Returns the player's highest gameplay grade: "mvp" > "vip+" > "vip" > "default". */
    String getGrade(Player player);

    /** Returns true if the player has the cosmetic Founder rank. */
    boolean isFounder(Player player);

    /** No-op backend: grants everything in singleplayer, returns "default" grade. */
    PermissionBackend NOOP = new PermissionBackend() {
        @Override public boolean hasPermission(Player player, String node) { return true; }
        @Override public String getGrade(Player player) { return "default"; }
        @Override public boolean isFounder(Player player) { return false; }
    };
}
