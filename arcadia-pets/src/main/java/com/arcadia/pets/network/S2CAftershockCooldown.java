package com.arcadia.pets.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → client: aftershock cooldown started; client shows the HUD drain bar. */
public record S2CAftershockCooldown(int cooldownMs)
        implements CustomPacketPayload {

    public static final Type<S2CAftershockCooldown> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "aftershock_cd"));

    public static final StreamCodec<FriendlyByteBuf, S2CAftershockCooldown> STREAM_CODEC =
            StreamCodec.of(S2CAftershockCooldown::encode, S2CAftershockCooldown::decode);

    private static void encode(FriendlyByteBuf buf, S2CAftershockCooldown pkt) {
        buf.writeInt(pkt.cooldownMs);
    }

    private static S2CAftershockCooldown decode(FriendlyByteBuf buf) {
        return new S2CAftershockCooldown(buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                com.arcadia.pets.client.ClientAftershockState.start(cooldownMs));
    }
}
