package com.arcadia.prestige.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record S2CParticleSync(UUID playerUuid, String particleId) implements CustomPacketPayload {

    public static final Type<S2CParticleSync> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_prestige", "particle_sync"));

    public static final StreamCodec<FriendlyByteBuf, S2CParticleSync> STREAM_CODEC =
            StreamCodec.of(S2CParticleSync::encode, S2CParticleSync::decode);

    private static void encode(FriendlyByteBuf buf, S2CParticleSync pkt) {
        buf.writeUUID(pkt.playerUuid);
        buf.writeUtf(pkt.particleId);
    }

    private static S2CParticleSync decode(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        String particleId = buf.readUtf();
        return new S2CParticleSync(uuid, particleId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            com.arcadia.prestige.client.PlayerEffectCache.update(playerUuid, particleId);
        });
    }
}
