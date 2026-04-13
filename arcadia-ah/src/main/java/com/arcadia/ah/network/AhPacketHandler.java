package com.arcadia.ah.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AhPacketHandler {

    private AhPacketHandler() {}

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                S2COpenAhSearch.TYPE,
                S2COpenAhSearch.STREAM_CODEC,
                (p, ctx) -> p.handle(ctx)
        );
        registrar.playToClient(
                S2COpenAhSell.TYPE,
                S2COpenAhSell.STREAM_CODEC,
                (p, ctx) -> p.handle(ctx)
        );
        registrar.playToServer(
                C2SAhSearch.TYPE,
                C2SAhSearch.STREAM_CODEC,
                C2SAhSearch::handle
        );
        registrar.playToServer(
                C2SAhSell.TYPE,
                C2SAhSell.STREAM_CODEC,
                C2SAhSell::handle
        );
    }
}
