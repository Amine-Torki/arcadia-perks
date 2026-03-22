package com.arcadia.pets.network;

import com.arcadia.pets.server.PetManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client → Server: rename a pet item.
 * {@code name} may be empty to clear the pet's custom name.
 */
public record C2SRenamePet(UUID petId, String name) implements CustomPacketPayload {

    public static final Type<C2SRenamePet> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_prestige", "rename_pet"));

    public static final StreamCodec<FriendlyByteBuf, C2SRenamePet> STREAM_CODEC =
            StreamCodec.of(C2SRenamePet::encode, C2SRenamePet::decode);

    private static void encode(FriendlyByteBuf buf, C2SRenamePet pkt) {
        buf.writeUUID(pkt.petId);
        buf.writeUtf(pkt.name, 20);
    }

    private static C2SRenamePet decode(FriendlyByteBuf buf) {
        return new C2SRenamePet(buf.readUUID(), buf.readUtf(20));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                PetManager.handleRename(sp, petId, name);
            }
        });
    }
}
