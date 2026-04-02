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
 * <p>Also grants the Arcadia Pass bonus XP (+1 per roster pet) to winners who
 * hold the {@code arcadia.pass} permission, on top of the base 2 XP already
 * granted by arcadia-pets. This keeps the bonus purely cosmetic / progression-speed,
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

        // Arcadia Pass bonus: +1 XP per winner roster pet (50% of the base 2 XP)
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerPlayer winner = (ServerPlayer) server.getPlayerList().getPlayer(event.getWinnerUuid());
        if (winner != null && LuckPermsHook.hasPass(winner)) {
            for (UUID petId : event.getWinnerPetIds()) {
                com.arcadia.pets.server.PetManager.addSkillXpToPet(winner, petId, 1);
            }
        }
    }
}
