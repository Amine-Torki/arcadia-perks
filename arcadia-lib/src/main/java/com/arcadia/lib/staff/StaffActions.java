package com.arcadia.lib.staff;

import com.arcadia.lib.ArcadiaMessages;
import com.arcadia.lib.text.TextFormatter;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moderation actions: mute/unmute with staff notifications and logging.
 * Ban/kick handled by external moderation mods.
 */
public final class StaffActions {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Active mutes: UUID → expiry timestamp. */
    private static final Map<UUID, Long> mutes = new ConcurrentHashMap<>();
    private static final Map<UUID, String> muteReasons = new ConcurrentHashMap<>();

    private StaffActions() {}

    // ── Mute ────────────────────────────────────────────────────────────────

    public static void mute(UUID target, ServerPlayer executor, String reason, long durationMs) {
        String r = reason != null && !reason.isEmpty() ? reason : Component.translatable("arcadia_lib.staff.no_reason").getString();
        mutes.put(target, System.currentTimeMillis() + durationMs);
        muteReasons.put(target, r);

        String targetName = target.toString();
        ServerPlayer online = com.arcadia.lib.player.PlayerManager.getPlayer(target);
        if (online != null) {
            targetName = online.getName().getString();
            online.sendSystemMessage(ArcadiaMessages.error(
                    Component.translatable("arcadia_lib.staff.muted_notify",
                            TextFormatter.formatMs(durationMs), r).getString()));
        }
        String finalName = targetName;
        StaffChatService.broadcastAlert(
                Component.translatable("arcadia_lib.staff.muted_alert",
                        executor.getName().getString(), finalName,
                        TextFormatter.formatMs(durationMs), r).getString());
        LOGGER.info("[ArcadiaStaff] {} muted {} for {}ms ({})",
                executor.getName().getString(), finalName, durationMs, r);
    }

    public static void unmute(UUID target, ServerPlayer executor) {
        mutes.remove(target);
        muteReasons.remove(target);
        ServerPlayer online = com.arcadia.lib.player.PlayerManager.getPlayer(target);
        if (online != null) {
            online.sendSystemMessage(ArcadiaMessages.success(
                    Component.translatable("arcadia_lib.staff.unmuted_notify").getString()));
        }
        StaffChatService.broadcastAlert(
                Component.translatable("arcadia_lib.staff.unmuted_alert",
                        executor.getName().getString(), target).getString());
    }

    /** Returns true if the player is currently muted. */
    public static boolean isMuted(UUID uuid) {
        Long expiry = mutes.get(uuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            mutes.remove(uuid);
            muteReasons.remove(uuid);
            return false;
        }
        return true;
    }

    public static long getMuteRemaining(UUID uuid) {
        Long expiry = mutes.get(uuid);
        if (expiry == null) return 0;
        return Math.max(0, expiry - System.currentTimeMillis());
    }

    public static String getMuteReason(UUID uuid) {
        return muteReasons.get(uuid);
    }
}
