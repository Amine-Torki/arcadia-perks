package com.arcadia.pets.elo;

import com.arcadia.lib.event.DuelResultEvent;
import com.arcadia.lib.permissions.PermissionService;
import com.arcadia.pets.ArcadiaPets;
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
 * <p>Registered on the game bus so it can receive events fired by arcadia-pets.</p>
 */
@EventBusSubscriber(modid = ArcadiaPets.MOD_ID)
public final class EloEventHandler {

    private EloEventHandler() {}

    @SubscribeEvent
    public static void onDuelResult(DuelResultEvent event) {
        // Skip ELO updates for bot practice duels
        UUID botUuid = UUID.fromString("00000000-0000-0000-0000-000000000B07");
        if (event.getWinnerUuid().equals(botUuid) || event.getLoserUuid().equals(botUuid)) return;

        EloManager.EloResult elo = EloManager.updateAfterDuel(
                event.getWinnerUuid(),
                event.getLoserUuid(),
                event.getWinnerMobType(),
                event.getLoserMobType());

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

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
