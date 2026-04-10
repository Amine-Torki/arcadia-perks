package com.arcadia.lib.permissions;

import com.mojang.logging.LogUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * LuckPerms-backed permission implementation. Gracefully handles the case where
 * LuckPerms is not installed (falls back to NOOP).
 *
 * <p>Use {@link #createOrFallback()} to safely obtain a backend instance.</p>
 */
public final class LuckPermsBackend implements PermissionBackend {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final LuckPerms api;

    private LuckPermsBackend(LuckPerms api) {
        this.api = api;
    }

    /**
     * Attempts to create a LuckPerms backend. Returns {@link PermissionBackend#NOOP}
     * if LuckPerms is not installed or not yet initialized.
     */
    public static PermissionBackend createOrFallback() {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            LOGGER.info("[ArcadiaLib] LuckPerms integration initialized.");
            return new LuckPermsBackend(lp);
        } catch (Throwable e) {
            LOGGER.warn("[ArcadiaLib] LuckPerms not available — using permissive fallback. ({})",
                    e.getClass().getSimpleName());
            return PermissionBackend.NOOP;
        }
    }

    @Override
    public boolean hasPermission(Player player, String node) {
        User user = api.getUserManager().getUser(player.getUUID());
        if (user == null) return false;
        return user.getCachedData()
                .getPermissionData(QueryOptions.defaultContextualOptions())
                .checkPermission(node)
                .asBoolean();
    }

    @Override
    public String getGrade(Player player) {
        User user = api.getUserManager().getUser(player.getUUID());
        if (user == null) return "default";

        if (hasNode(user, PermissionConfig.GRADE_PERM_MVP))      return "mvp";
        if (hasNode(user, PermissionConfig.GRADE_PERM_VIP_PLUS)) return "vip+";
        if (hasNode(user, PermissionConfig.GRADE_PERM_VIP))      return "vip";
        return "default";
    }

    @Override
    public boolean isFounder(Player player) {
        User user = api.getUserManager().getUser(player.getUUID());
        if (user == null) return false;
        return hasNode(user, PermissionConfig.GRADE_PERM_FOUNDER);
    }

    private boolean hasNode(User user, String permission) {
        return user.getCachedData()
                .getPermissionData(QueryOptions.defaultContextualOptions())
                .checkPermission(permission)
                .asBoolean();
    }
}
