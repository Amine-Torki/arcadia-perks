package com.arcadia.prestige.elo;

import com.arcadia.lib.event.DuelResultEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * NeoForge event listener that updates ELO ratings when a pet duel ends.
 *
 * <p>Registered on the {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}
 * (game bus, not mod bus) so it can receive events fired by arcadia-pets.</p>
 */
@EventBusSubscriber(modid = "arcadia_prestige")
public final class EloEventHandler {

    private EloEventHandler() {}

    @SubscribeEvent
    public static void onDuelResult(DuelResultEvent event) {
        EloManager.updateAfterDuel(
                event.getWinnerUuid(),
                event.getLoserUuid(),
                event.getWinnerMobType(),
                event.getLoserMobType());
    }
}
