package com.arcadia.pets.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetRarity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public record S2CPetReveal(CompoundTag petTag, byte minimumRarityOrdinal) implements CustomPacketPayload {

    public static final Type<S2CPetReveal> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_prestige", "pet_reveal"));

    public static final StreamCodec<FriendlyByteBuf, S2CPetReveal> STREAM_CODEC =
            StreamCodec.of(S2CPetReveal::encode, S2CPetReveal::decode);

    private static void encode(FriendlyByteBuf buf, S2CPetReveal pkt) {
        buf.writeNbt(pkt.petTag);
        buf.writeByte(pkt.minimumRarityOrdinal);
    }

    private static S2CPetReveal decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        byte ordinal = buf.readByte();
        return new S2CPetReveal(tag, ordinal);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            PetData data = PetData.fromTag(petTag);
            PetRarity[] rarities = PetRarity.values();
            PetRarity minRarity = (minimumRarityOrdinal >= 0 && minimumRarityOrdinal < rarities.length)
                    ? rarities[minimumRarityOrdinal] : PetRarity.COMMON;
            Minecraft.getInstance().setScreen(new com.arcadia.pets.client.PetRevealScreen(data, minRarity));
        });
    }
}
