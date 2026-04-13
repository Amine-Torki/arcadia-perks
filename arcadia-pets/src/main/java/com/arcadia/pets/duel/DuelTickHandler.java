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
 * Enforces the per-turn action deadline defined in {@link DuelSession#TURN_TIMEOUT_MS}.
 *
 * <p>Runs once per second on the server tick. If the acting pet's owner has not
 * submitted an action before the deadline, the turn is automatically passed with
 * a timeout notice in the combat log. SP is preserved (normal pass behaviour).</p>
 */
@EventBusSubscriber(modid = ArcadiaPets.MOD_ID)
public final class DuelTickHandler {

    /** Tick counter — we only need to check once per second, not every tick. */
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

            TurnSlot slot = session.currentSlot();
            if (slot == null) continue;

            boolean isBotTurn = slot.ownerUuid().equals(DuelManager.BOT_UUID);

            if (isBotTurn) {
                // Schedule bot think delay on first tick of bot's turn
                if (session.botActAt == 0L) {
                    session.botActAt = now + 1_500L;
                    continue;
                }
                // Execute bot action once delay has passed
                if (now >= session.botActAt) {
                    DuelManager.executeBotTurn(session);
                    if (session.checkWinCondition()) {
                        DuelManager.endDuel(session, session.winner);
                    }
                    broadcastState(server, session);
                }
                continue;
            }

            // Human turn timeout
            if (session.actionDeadline <= 0 || now < session.actionDeadline) continue;

            String petName = session.petName(slot.ownerUuid(), slot.petIndex());
            session.addLog("⏰ " + petName + " timed out! Turn auto-passed, SP saved.");
            session.endTurn();

            // Reset bot timer if bot's turn is next
            session.botActAt = 0L;

            if (session.checkWinCondition()) {
                DuelManager.endDuel(session, session.winner);
            }

            broadcastState(server, session);
        }
    }
}
