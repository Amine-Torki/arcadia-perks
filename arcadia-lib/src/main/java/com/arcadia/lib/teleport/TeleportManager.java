package com.arcadia.lib.teleport;

import com.arcadia.lib.ArcadiaMessages;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized teleportation system for all Arcadia mods.
 * Supports warmup delays, cooldowns, movement cancellation, and cross-dimension teleport.
 *
 * <p>Usage: {@code TeleportManager.teleport(player, targetPos, level, options)}</p>
 */
public final class TeleportManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Active warmup tasks waiting to execute. */
    private static final Map<UUID, WarmupTask> activeWarmups = new ConcurrentHashMap<>();

    /** Cooldown timestamps per player per action. */
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    private TeleportManager() {}

    // ── Instant teleport ────────────────────────────────────────────────────

    /** Teleports a player instantly to a position in the same dimension. */
    public static void teleportNow(ServerPlayer player, Vec3 target) {
        teleportNow(player, target, player.serverLevel());
    }

    /** Teleports a player instantly to a position in a specific dimension. */
    public static void teleportNow(ServerPlayer player, Vec3 target, ServerLevel level) {
        player.teleportTo(level, target.x, target.y, target.z,
                java.util.Set.of(), player.getYRot(), player.getXRot());
        level.playSound(null, BlockPos.containing(target),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    // ── Teleport with warmup ────────────────────────────────────────────────

    /**
     * Starts a teleport with a warmup delay. The player must stand still during warmup.
     * Movement cancels the teleport. Uses the default action ID "teleport".
     *
     * @param player     the player to teleport
     * @param target     destination position
     * @param level      destination dimension
     * @param warmupTicks warmup duration in ticks (20 = 1 second)
     * @param cooldownMs  cooldown after teleport in milliseconds (0 = none)
     */
    public static void teleportWithWarmup(ServerPlayer player, Vec3 target, ServerLevel level,
                                          int warmupTicks, long cooldownMs) {
        teleportWithWarmup(player, target, level, warmupTicks, cooldownMs, "teleport");
    }

    /**
     * Starts a teleport with a warmup delay and a named action for cooldown tracking.
     */
    public static void teleportWithWarmup(ServerPlayer player, Vec3 target, ServerLevel level,
                                          int warmupTicks, long cooldownMs, String actionId) {
        UUID uuid = player.getUUID();

        // Check cooldown
        String cdKey = uuid + ":" + actionId;
        Long cdEnd = cooldowns.get(cdKey);
        if (cdEnd != null && System.currentTimeMillis() < cdEnd) {
            long remaining = cdEnd - System.currentTimeMillis();
            player.sendSystemMessage(ArcadiaMessages.error(
                    "Teleport on cooldown (" + com.arcadia.lib.text.TextFormatter.formatMs(remaining) + ")"));
            return;
        }

        // Cancel existing warmup
        cancelWarmup(uuid);

        // Start new warmup
        Vec3 startPos = player.position();
        activeWarmups.put(uuid, new WarmupTask(target, level, warmupTicks, cooldownMs,
                actionId, startPos, System.currentTimeMillis()));

        if (warmupTicks > 0) {
            player.sendSystemMessage(ArcadiaMessages.info(
                    "Teleporting in " + com.arcadia.lib.text.TextFormatter.formatTicks(warmupTicks)
                    + "... Don't move!"));
        }
    }

    /**
     * Must be called every server tick to process warmup timers.
     * Typically called from a ServerTickEvent handler.
     */
    public static void tick() {
        var it = activeWarmups.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID uuid = entry.getKey();
            WarmupTask task = entry.getValue();

            // Find the player
            ServerPlayer player = findPlayer(uuid);
            if (player == null) { it.remove(); continue; }

            // Check movement (> 0.3 blocks = cancelled)
            double dist = player.position().distanceTo(task.startPos);
            if (dist > 0.3) {
                player.sendSystemMessage(ArcadiaMessages.error("Teleport cancelled — you moved!"));
                it.remove();
                continue;
            }

            // Count down
            long elapsed = (System.currentTimeMillis() - task.startTime);
            int elapsedTicks = (int) (elapsed / 50);
            if (elapsedTicks >= task.warmupTicks) {
                // Execute teleport
                teleportNow(player, task.target, task.level);
                player.sendSystemMessage(ArcadiaMessages.success("Teleported!"));

                // Set cooldown
                if (task.cooldownMs > 0) {
                    cooldowns.put(uuid + ":" + task.actionId, System.currentTimeMillis() + task.cooldownMs);
                }
                it.remove();
            }
        }
    }

    /** Cancels any active warmup for a player. */
    public static void cancelWarmup(UUID uuid) {
        activeWarmups.remove(uuid);
    }

    /** Checks if a player has an active warmup. */
    public static boolean hasWarmup(UUID uuid) {
        return activeWarmups.containsKey(uuid);
    }

    /** Checks if an action is on cooldown for a player. Returns remaining ms, or 0 if ready. */
    public static long getCooldownRemaining(UUID uuid, String actionId) {
        Long cdEnd = cooldowns.get(uuid + ":" + actionId);
        if (cdEnd == null) return 0;
        return Math.max(0, cdEnd - System.currentTimeMillis());
    }

    /** Cleans up data for a player on disconnect. */
    public static void onPlayerDisconnect(UUID uuid) {
        activeWarmups.remove(uuid);
        cooldowns.entrySet().removeIf(e -> e.getKey().startsWith(uuid.toString()));
    }

    private static ServerPlayer findPlayer(UUID uuid) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getPlayerList().getPlayer(uuid) : null;
    }

    private record WarmupTask(Vec3 target, ServerLevel level, int warmupTicks,
                              long cooldownMs, String actionId, Vec3 startPos, long startTime) {}
}
