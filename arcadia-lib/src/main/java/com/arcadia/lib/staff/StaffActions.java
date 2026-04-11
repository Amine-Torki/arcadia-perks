package com.arcadia.lib.staff;

import com.arcadia.lib.ArcadiaMessages;
import com.arcadia.lib.text.TextFormatter;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import org.slf4j.Logger;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standardized moderation actions with staff notifications and logging.
 */
public final class StaffActions {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Active mutes: UUID → expiry timestamp (System.currentTimeMillis). */
    private static final Map<UUID, Long> mutes = new ConcurrentHashMap<>();
    private static final Map<UUID, String> muteReasons = new ConcurrentHashMap<>();

    private StaffActions() {}

    // ── Kick ────────────────────────────────────────────────────────────────

    public static void kick(ServerPlayer target, ServerPlayer executor, String reason) {
        String r = reason != null && !reason.isEmpty() ? reason : "No reason specified";
        String targetName = target.getName().getString();
        String execName = executor.getName().getString();

        target.connection.disconnect(Component.literal("Kicked: " + r));

        StaffChatService.broadcastAlert(execName + " kicked " + targetName + " (" + r + ")");
        LOGGER.info("[ArcadiaStaff] {} kicked {} ({})", execName, targetName, r);
    }

    // ── Ban ─────────────────────────────────────────────────────────────────

    public static void ban(ServerPlayer target, ServerPlayer executor, String reason, long durationMs) {
        String r = reason != null && !reason.isEmpty() ? reason : "No reason specified";
        String targetName = target.getName().getString();
        String execName = executor.getName().getString();
        MinecraftServer server = executor.getServer();
        if (server == null) return;

        Date expires = durationMs > 0 ? new Date(System.currentTimeMillis() + durationMs) : null;
        UserBanList banList = server.getPlayerList().getBans();
        banList.add(new UserBanListEntry(target.getGameProfile(), null, execName, expires, r));

        String durStr = durationMs > 0 ? TextFormatter.formatMs(durationMs) : "permanent";
        target.connection.disconnect(Component.literal("Banned: " + r + " (" + durStr + ")"));

        StaffChatService.broadcastAlert(execName + " banned " + targetName
                + " for " + durStr + " (" + r + ")");
        LOGGER.info("[ArcadiaStaff] {} banned {} for {} ({})", execName, targetName, durStr, r);
    }

    // ── Mute ────────────────────────────────────────────────────────────────

    public static void mute(UUID target, ServerPlayer executor, String reason, long durationMs) {
        String r = reason != null && !reason.isEmpty() ? reason : "No reason specified";
        mutes.put(target, System.currentTimeMillis() + durationMs);
        muteReasons.put(target, r);

        String targetName = target.toString();
        ServerPlayer online = com.arcadia.lib.player.PlayerManager.getPlayer(target);
        if (online != null) {
            targetName = online.getName().getString();
            online.sendSystemMessage(ArcadiaMessages.error(
                    "You have been muted for " + TextFormatter.formatMs(durationMs) + ": " + r));
        }
        StaffChatService.broadcastAlert(executor.getName().getString()
                + " muted " + targetName + " for " + TextFormatter.formatMs(durationMs) + " (" + r + ")");
        LOGGER.info("[ArcadiaStaff] {} muted {} for {}ms ({})",
                executor.getName().getString(), targetName, durationMs, r);
    }

    public static void unmute(UUID target, ServerPlayer executor) {
        mutes.remove(target);
        muteReasons.remove(target);
        ServerPlayer online = com.arcadia.lib.player.PlayerManager.getPlayer(target);
        if (online != null) {
            online.sendSystemMessage(ArcadiaMessages.success("You have been unmuted."));
        }
        StaffChatService.broadcastAlert(executor.getName().getString() + " unmuted " + target);
    }

    /** Returns true if the player is currently muted. Auto-clears expired mutes. */
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

    /** Returns remaining mute time in ms, or 0 if not muted. */
    public static long getMuteRemaining(UUID uuid) {
        Long expiry = mutes.get(uuid);
        if (expiry == null) return 0;
        return Math.max(0, expiry - System.currentTimeMillis());
    }

    /** Returns the mute reason, or null. */
    public static String getMuteReason(UUID uuid) {
        return muteReasons.get(uuid);
    }

    /** Cleanup on disconnect. */
    public static void onDisconnect(UUID uuid) {
        // Keep mutes active across reconnects — don't clear here
    }
}
