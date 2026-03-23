package com.arcadia.pets.network;

import com.arcadia.pets.server.SkillHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: sent every ~40 ticks when the client detects hostile mobs nearby.
 * Server validates range per skill and applies entity-scanning aura effects (Wither Aura, Soul Drain).
 * Offloads proximity detection to the client to keep the server tick lean.
 */
public record C2SAuraTick() implements CustomPacketPayload {

    public static final Type<C2SAuraTick> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "aura_tick"));

    public static final StreamCodec<FriendlyByteBuf, C2SAuraTick> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new C2SAuraTick());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                SkillHandler.triggerAuraTick(sp);
            }
        });
    }
}
