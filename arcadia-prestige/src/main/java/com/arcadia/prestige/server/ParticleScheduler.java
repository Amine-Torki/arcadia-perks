package com.arcadia.prestige.server;

import com.arcadia.prestige.ArcadiaDashboard;
// PetManager.onPlayerLogout is now called via PlayerManager.onQuit callback in arcadia-pets
import com.arcadia.lib.data.DatabaseManager;
import com.arcadia.lib.data.PlayerDataHandler;
import com.arcadia.prestige.network.PacketHandler;
import com.arcadia.prestige.network.S2CParticleSync;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for player login and logout events on the game event bus to synchronise
 * particle effects across all connected clients and to clean up pet state.
 */
@EventBusSubscriber(modid = ArcadiaDashboard.MOD_ID)
public final class ParticleScheduler {

    /** Tracks the currently active particle effect ID for every online player. */
    private static final Map<UUID, String> activeParticles = new ConcurrentHashMap<>();

    private ParticleScheduler() {}

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    /**
     * When a player logs in:
     * <ol>
     *   <li>Load their saved particle effect from the database asynchronously.</li>
     *   <li>Broadcast that effect to all currently connected players.</li>
     *   <li>Send all existing active effects to the newly joined player.</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joiner)) {
            return;
        }

        UUID joinerUuid = joiner.getUUID();
        MinecraftServer server = joiner.getServer();
        if (server == null) {
            return;
        }

        DatabaseManager.executeAsync(() -> {
            String particleId = PlayerDataHandler.getParticle(joinerUuid);
            PlayerDataHandler.PlayerRecord rec = PlayerDataHandler.loadPlayer(joinerUuid);

            server.execute(() -> {
                // 1. Register and broadcast the joining player's own effect (if any).
                if (particleId != null && !particleId.isEmpty()) {
                    activeParticles.put(joinerUuid, particleId);
                    PacketHandler.sendToAll(server, new S2CParticleSync(joinerUuid, particleId));
                }

                // 2. Send every existing active effect to the joiner so their client is up to date.
                for (Map.Entry<UUID, String> entry : activeParticles.entrySet()) {
                    if (!entry.getKey().equals(joinerUuid)) {
                        PacketHandler.sendToPlayer(joiner, new S2CParticleSync(entry.getKey(), entry.getValue()));
                    }
                }

                // 3. Daily reward notifications
                long now     = System.currentTimeMillis();
                long elapsed = now - rec.lastClaim();
                long day24h  = 24L * 60 * 60 * 1000;
                long day48h  = 48L * 60 * 60 * 1000;
                if (elapsed >= day24h) {
                    if (rec.lastClaim() != 0L && elapsed > day48h) {
                        joiner.sendSystemMessage(Component.translatable("arcadia_prestige.msg.streak_reset")
                                .withStyle(ChatFormatting.RED));
                    }
                    joiner.sendSystemMessage(Component.translatable("arcadia_prestige.msg.daily_ready")
                            .withStyle(ChatFormatting.GOLD));
                }
            });
        });
    }

    /**
     * When a player logs out:
     * <ol>
     *   <li>Remove their particle entry from the local map.</li>
     *   <li>Broadcast an empty particle ID to all clients so they clear the effect.</li>
     *   <li>Despawn the player's active pet.</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();
        activeParticles.remove(uuid);

        MinecraftServer server = player.getServer();
        if (server != null) {
            // Empty string signals clients to remove the effect for this player.
            PacketHandler.sendToAll(server, new S2CParticleSync(uuid, ""));
        }

        // Pet logout handled by arcadia-pets via PlayerManager.onQuit callback
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Updates the active particle for a player and immediately notifies all clients.
     * Passing {@code null} or an empty string removes the player's effect.
     *
     * @param player     the server player whose effect is changing
     * @param particleId the new particle effect ID, or empty/null to clear
     */
    public static void broadcastParticleChange(ServerPlayer player, String particleId) {
        UUID uuid = player.getUUID();

        if (particleId == null || particleId.isEmpty()) {
            activeParticles.remove(uuid);
        } else {
            activeParticles.put(uuid, particleId);
        }

        String effectiveId = (particleId != null) ? particleId : "";
        PacketHandler.sendToAll(player.getServer(), new S2CParticleSync(uuid, effectiveId));
    }
}
