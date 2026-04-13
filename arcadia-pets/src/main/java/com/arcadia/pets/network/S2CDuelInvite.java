package com.arcadia.pets.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: notifies a player that they have received a duel challenge.
 *
 * <p>The client displays a toast overlay with [Accept] / [Decline] buttons.
 * Clicking either button sends a {@link C2SDuelAction} with the corresponding
 * action ID (ACCEPT = 10, DECLINE = 11).</p>
 *
 * @param challengerName  display name of the challenging player
 * @param petCount        number of pets in the challenger's collection (for preview)
 */
public record S2CDuelInvite(String challengerName, int petCount) implements CustomPacketPayload {

    public static final Type<S2CDuelInvite> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "duel_invite"));

    public static final StreamCodec<FriendlyByteBuf, S2CDuelInvite> STREAM_CODEC =
            StreamCodec.of(S2CDuelInvite::encode, S2CDuelInvite::decode);

    private static void encode(FriendlyByteBuf buf, S2CDuelInvite pkt) {
        buf.writeUtf(pkt.challengerName);
        buf.writeVarInt(pkt.petCount);
    }

    private static S2CDuelInvite decode(FriendlyByteBuf buf) {
        return new S2CDuelInvite(buf.readUtf(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                com.arcadia.pets.client.DuelInviteOverlay.showInvite(challengerName));
    }
}
