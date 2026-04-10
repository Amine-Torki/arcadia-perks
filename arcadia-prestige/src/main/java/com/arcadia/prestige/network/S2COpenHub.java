package com.arcadia.prestige.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: open the Prestige Hub screen (client-side only, no container menu).
 */
public record S2COpenHub() implements CustomPacketPayload {

    public static final Type<S2COpenHub> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_prestige", "open_hub"));

    public static final StreamCodec<FriendlyByteBuf, S2COpenHub> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new S2COpenHub());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.arcadia.lib.client.ArcadiaHubScreen.open());
    }
}
