package com.arcadia.pets.duel;

import com.arcadia.pets.ArcadiaPets;
import com.arcadia.pets.network.S2CDuelState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Enforces the per-turn action deadline ({@link DuelSession#TURN_TIMEOUT_MS}) and
 * drives the bot's artificial think delay.
 *
 * <p>Runs once per second on the server tick.</p>
 */
@EventBusSubscriber(modid = ArcadiaPets.MOD_ID)
public final class DuelTickHandler {

    private static int tick = 0;

    private DuelTickHandler() {}

    private static void broadcastState(MinecraftServer server, DuelSession session) {
        S2CDuelState state = S2CDuelState.from(session);
        ServerPlayer p1 = server.getPlayerList().getPlayer(session.p1);
        ServerPlayer p2 = server.getPlayerList().getPlayer(session.p2);
        if (p1 != null) PacketDistributor.sendToPlayer(p1, state);
        if (p2 != null) PacketDistributor.sendToPlayer(p2, state);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tick % 20 != 0) return;
        MinecraftServer server = event.getServer();
        long now = System.currentTimeMillis();

        for (DuelSession session : DuelManager.getActiveSessions()) {
            if (session.phase != DuelPhase.ACTIVE) continue;
            if (session.currentTurnPlayer == null) continue;

            boolean isBotTurn = DuelManager.BOT_UUID.equals(session.currentTurnPlayer);

            if (isBotTurn) {
                // Schedule bot think delay on first tick of bot's turn
                if (session.botActAt == 0L) {
                    session.botActAt = now + 1_500L;
                    continue;
                }
                // Execute all bot pet actions once delay has passed
                if (now >= session.botActAt) {
                    DuelManager.executeBotTurn(session);
                    if (session.checkWinCondition()) {
                        DuelManager.endDuel(session, session.winner);
                    }
                    broadcastState(server, session);
                }
                continue;
            }

            // Human turn timeout: auto-pass all remaining pending pets
            if (session.actionDeadline <= 0 || now < session.actionDeadline) continue;

            session.addLog("⏰ Turn timed out! All remaining pet actions skipped. SP carried over.");
            session.pendingPetActions.clear();
            session.endPlayerTurn(); // ticks effects and opens opponent's turn

            session.botActAt = 0L; // reset bot timer if bot is next

            if (session.checkWinCondition()) {
                DuelManager.endDuel(session, session.winner);
            }

            broadcastState(server, session);
        }
    }
}
