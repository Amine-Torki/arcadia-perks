package com.arcadia.pets.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Server → Client: broadcasts the opponent's current selection hint.
 * Mirrors {@link C2SDuelHint} but adds the sender UUID so the receiver can
 * ignore echoes of its own hints.
 */
public record S2CDuelHint(UUID senderUuid, int hintType, int petIdx)
        implements CustomPacketPayload {

    public static final Type<S2CDuelHint> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "duel_hint_s2c"));

    public static final StreamCodec<FriendlyByteBuf, S2CDuelHint> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeUUID(pkt.senderUuid);
                        buf.writeVarInt(pkt.hintType);
                        buf.writeVarInt(pkt.petIdx);
                    },
                    buf -> new S2CDuelHint(buf.readUUID(), buf.readVarInt(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public static void handle(S2CDuelHint pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            UUID local = mc.player != null ? mc.player.getUUID() : null;
            if (pkt.senderUuid().equals(local)) return; // ignore our own echo
            com.arcadia.pets.client.DuelClientState.updateOpponentHint(pkt.hintType(), pkt.petIdx());
        });
    }
}
