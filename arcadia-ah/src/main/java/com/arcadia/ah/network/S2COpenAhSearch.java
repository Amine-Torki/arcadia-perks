package com.arcadia.ah.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: open the AH search text-input screen.
 * Carries the current query so the field is pre-populated.
 */
public record S2COpenAhSearch(String currentQuery) implements CustomPacketPayload {

    public static final Type<S2COpenAhSearch> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_ah", "open_ah_search"));

    public static final StreamCodec<FriendlyByteBuf, S2COpenAhSearch> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeUtf(pkt.currentQuery()),
                    buf -> new S2COpenAhSearch(buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.arcadia.ah.client.AhSearchScreen.open(currentQuery));
    }
}
