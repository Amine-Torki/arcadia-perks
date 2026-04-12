package com.arcadia.lib.network;

import com.arcadia.lib.ArcadiaLib;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network packet registration for arcadia-lib.
 * Handles hub open packet and any future lib-level S2C/C2S communication.
 */
public final class ArcadiaLibNet {

    private ArcadiaLibNet() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                S2COpenHub.TYPE,
                S2COpenHub.STREAM_CODEC,
                (p, ctx) -> p.handle(ctx)
        );
    }

    /** Sends the open-hub packet to a player. */
    public static void sendOpenHub(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new S2COpenHub());
    }
}
