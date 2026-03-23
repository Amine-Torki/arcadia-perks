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
 * Server → all clients within 64 blocks: tells the client to render a floating
 * pocket pet for {@code ownerUuid} using a local fake entity of {@code mobType}.
 *
 * <p>When {@code mobType} is {@code ""} (empty string), the client clears the
 * pocket pet for that owner (recall / mode switch).</p>
 *
 * No real server entity is spawned in pocket mode — the server just tracks pet
 * data in a plain map. This packet carries everything the client needs to
 * reconstruct the visual via {@link com.arcadia.pets.client.PocketPetRenderer}.
 */
public record S2CPocketPet(UUID ownerUuid, String mobType, float scale, String customName) implements CustomPacketPayload {

    /** Convenience factory for the "recall / clear" signal. */
    public static S2CPocketPet recall(UUID ownerUuid) {
        return new S2CPocketPet(ownerUuid, "", 1f, "");
    }

    public static final Type<S2CPocketPet> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pocket_pet"));

    public static final StreamCodec<FriendlyByteBuf, S2CPocketPet> STREAM_CODEC =
            StreamCodec.of(S2CPocketPet::encode, S2CPocketPet::decode);

    private static void encode(FriendlyByteBuf buf, S2CPocketPet pkt) {
        buf.writeUUID(pkt.ownerUuid);
        buf.writeUtf(pkt.mobType);
        buf.writeFloat(pkt.scale);
        buf.writeUtf(pkt.customName != null ? pkt.customName : "", 64);
    }

    private static S2CPocketPet decode(FriendlyByteBuf buf) {
        return new S2CPocketPet(buf.readUUID(), buf.readUtf(), buf.readFloat(), buf.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                com.arcadia.pets.client.PocketPetRenderer.onPacket(this));
    }
}
