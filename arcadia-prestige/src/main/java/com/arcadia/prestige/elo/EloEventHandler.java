package com.arcadia.prestige.elo;

import com.arcadia.lib.event.DuelResultEvent;
import com.arcadia.prestige.server.LuckPermsHook;
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
        // Update ELO for both players
        EloManager.updateAfterDuel(
                event.getWinnerUuid(),
                event.getLoserUuid(),
                event.getWinnerMobType(),
                event.getLoserMobType());

        // Arcadia Pass bonus: +1 XP per roster pet for any pass holder (winner or loser)
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerPlayer winner = (ServerPlayer) server.getPlayerList().getPlayer(event.getWinnerUuid());
        if (winner != null && LuckPermsHook.hasPass(winner)) {
            for (UUID petId : event.getWinnerPetIds()) {
                com.arcadia.pets.server.PetManager.addSkillXpToPet(winner, petId, 1);
            }
        }

        ServerPlayer loser = (ServerPlayer) server.getPlayerList().getPlayer(event.getLoserUuid());
        if (loser != null && LuckPermsHook.hasPass(loser)) {
            for (UUID petId : event.getLoserPetIds()) {
                com.arcadia.pets.server.PetManager.addSkillXpToPet(loser, petId, 1);
            }
        }
    }
}
