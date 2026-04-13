package com.arcadia.lib.network;

import com.arcadia.lib.ArcadiaLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: open the Arcadia Hub screen.
 * Lives in the lib so any mod can trigger it without depending on prestige.
 */
public record S2COpenHub() implements CustomPacketPayload {

    public static final Type<S2COpenHub> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaLib.MOD_ID, "open_hub"));

    public static final StreamCodec<FriendlyByteBuf, S2COpenHub> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new S2COpenHub());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.arcadia.lib.client.ArcadiaHubScreen.open());
    }
}
