package com.arcadia.prestige.elo;

import com.arcadia.lib.event.DuelResultEvent;
import com.arcadia.lib.permissions.PermissionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * NeoForge event listener that updates ELO ratings when a pet duel ends.
 *
 * <p>Registered on the {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}
 * (game bus, not mod bus) so it can receive events fired by arcadia-pets.</p>
 *
 * <p>Also grants the Arcadia Pass bonus XP (+1 per roster pet) to any pass holder,
 * whether they won or lost. Winners get base 2 XP + 1 pass = 3 XP total;
 * losers get base 1 XP + 1 pass = 2 XP total. Purely a progression-speed benefit,
 * not a PvP advantage.</p>
 */
@EventBusSubscriber(modid = "arcadia_prestige")
public final class EloEventHandler {

    private EloEventHandler() {}

    @SubscribeEvent
    public static void onDuelResult(DuelResultEvent event) {
        // Skip ELO updates for bot practice duels
        UUID botUuid = UUID.fromString("00000000-0000-0000-0000-000000000B07");
        if (event.getWinnerUuid().equals(botUuid) || event.getLoserUuid().equals(botUuid)) return;
        // Update ELO and capture deltas for feedback messages
        EloManager.EloResult elo = EloManager.updateAfterDuel(
                event.getWinnerUuid(),
                event.getLoserUuid(),
                event.getWinnerMobType(),
                event.getLoserMobType());

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Send ELO delta message to both players (if online)
        sendEloMessage(server, event.getWinnerUuid(), elo.winnerDelta(), elo.newWinnerRating());
        sendEloMessage(server, event.getLoserUuid(),  elo.loserDelta(),  elo.newLoserRating());

        // Arcadia Pass bonus: +1 XP per roster pet for any pass holder
        ServerPlayer winner = (ServerPlayer) server.getPlayerList().getPlayer(event.getWinnerUuid());
        if (winner != null && PermissionService.hasPermission(winner, "arcadia.pass")) {
            for (UUID petId : event.getWinnerPetIds()) {
                com.arcadia.lib.ArcadiaModRegistry.executeServerAction("pets.add_skill_xp_pet:" + petId + ":1", winner);
            }
        }

        ServerPlayer loser = (ServerPlayer) server.getPlayerList().getPlayer(event.getLoserUuid());
        if (loser != null && PermissionService.hasPermission(loser, "arcadia.pass")) {
            for (UUID petId : event.getLoserPetIds()) {
                com.arcadia.lib.ArcadiaModRegistry.executeServerAction("pets.add_skill_xp_pet:" + petId + ":1", loser);
            }
        }
    }

    private static void sendEloMessage(MinecraftServer server, UUID uuid, int delta, int newRating) {
        ServerPlayer sp = (ServerPlayer) server.getPlayerList().getPlayer(uuid);
        if (sp == null) return;
        String sign  = delta >= 0 ? "§a+" : "§c";
        String arrow = delta >= 0 ? "▲" : "▼";
        sp.sendSystemMessage(Component.literal(
                "§b[ELO] " + sign + delta + " " + arrow + " §e" + newRating + " ELO"));
    }
}
