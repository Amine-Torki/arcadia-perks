package com.arcadia.lib.player;

import com.arcadia.lib.ArcadiaLib;
import com.arcadia.lib.teleport.TeleportManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Centralized player lifecycle management for all Arcadia mods.
 * Tracks online players, fires join/quit callbacks, and ticks subsystems.
 *
 * <p>Mods register callbacks via {@link #onJoin(Consumer)} and {@link #onQuit(Consumer)}
 * instead of subscribing to raw events — ensures cleanup order and avoids missed events.</p>
 */
@EventBusSubscriber(modid = ArcadiaLib.MOD_ID)
public final class PlayerManager {

    /** Currently online players (UUID → ServerPlayer). Updated on join/quit. */
    private static final Map<UUID, ServerPlayer> onlinePlayers = new ConcurrentHashMap<>();

    /** Join callbacks — fired when a player logs in. */
    private static final List<Consumer<ServerPlayer>> joinCallbacks = new CopyOnWriteArrayList<>();

    /** Quit callbacks — fired when a player logs out. */
    private static final List<Consumer<ServerPlayer>> quitCallbacks = new CopyOnWriteArrayList<>();

    private PlayerManager() {}

    // ── Registration API ────────────────────────────────────────────────────

    /** Registers a callback fired when any player joins. */
    public static void onJoin(Consumer<ServerPlayer> callback) {
        joinCallbacks.add(callback);
    }

    /** Registers a callback fired when any player quits. */
    public static void onQuit(Consumer<ServerPlayer> callback) {
        quitCallbacks.add(callback);
    }

    // ── Query API ───────────────────────────────────────────────────────────

    /** Returns the ServerPlayer for a UUID, or null if offline. */
    public static ServerPlayer getPlayer(UUID uuid) {
        return onlinePlayers.get(uuid);
    }

    /** Returns true if the player is currently online. */
    public static boolean isOnline(UUID uuid) {
        return onlinePlayers.containsKey(uuid);
    }

    /** Returns an unmodifiable view of all online players. */
    public static Collection<ServerPlayer> getOnlinePlayers() {
        return Collections.unmodifiableCollection(onlinePlayers.values());
    }

    /** Returns the count of online players. */
    public static int getOnlineCount() {
        return onlinePlayers.size();
    }

    // ── Event handlers ──────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        onlinePlayers.put(sp.getUUID(), sp);
        for (Consumer<ServerPlayer> cb : joinCallbacks) {
            try { cb.accept(sp); }
            catch (Exception e) { /* prevent one callback from breaking others */ }
        }
    }

    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        for (Consumer<ServerPlayer> cb : quitCallbacks) {
            try { cb.accept(sp); }
            catch (Exception e) { /* prevent one callback from breaking others */ }
        }
        onlinePlayers.remove(sp.getUUID());
        TeleportManager.onPlayerDisconnect(sp.getUUID());
        CooldownManager.clearPlayer(sp.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TeleportManager.tick();
    }
}
