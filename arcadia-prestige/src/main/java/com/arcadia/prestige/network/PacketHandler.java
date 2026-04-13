package com.arcadia.prestige.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class PacketHandler {

    private PacketHandler() {}

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                S2CParticleSync.TYPE,
                S2CParticleSync.STREAM_CODEC,
                (p, ctx) -> p.handle(ctx)
        );
        registrar.playToServer(
                C2SDashboardAction.TYPE,
                C2SDashboardAction.STREAM_CODEC,
                C2SDashboardAction::handle
        );
        // S2COpenHub moved to arcadia-lib (ArcadiaLibNet)
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
