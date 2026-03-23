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

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: deliver one or more rolled pet results to display the reveal screen.
 * Size 1  → single-spin {@link com.arcadia.pets.client.PetRevealScreen}.
 * Size 2–4 → multi-spin {@link com.arcadia.pets.client.PetMultiRevealScreen}.
 */
public record S2CPetReveal(List<CompoundTag> petTags, byte minimumRarityOrdinal)
        implements CustomPacketPayload {

    public static final Type<S2CPetReveal> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pet_reveal"));

    public static final StreamCodec<FriendlyByteBuf, S2CPetReveal> STREAM_CODEC =
            StreamCodec.of(S2CPetReveal::encode, S2CPetReveal::decode);

    private static void encode(FriendlyByteBuf buf, S2CPetReveal pkt) {
        buf.writeByte(pkt.petTags.size());
        for (CompoundTag tag : pkt.petTags) buf.writeNbt(tag);
        buf.writeByte(pkt.minimumRarityOrdinal);
    }

    private static S2CPetReveal decode(FriendlyByteBuf buf) {
        int count = buf.readByte() & 0xFF;
        List<CompoundTag> tags = new ArrayList<>(count);
        for (int i = 0; i < count; i++) tags.add(buf.readNbt());
        byte ordinal = buf.readByte();
        return new S2CPetReveal(tags, ordinal);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            PetRarity[] rarities = PetRarity.values();
            PetRarity minRarity = (minimumRarityOrdinal >= 0 && minimumRarityOrdinal < rarities.length)
                    ? rarities[minimumRarityOrdinal] : PetRarity.COMMON;

            if (petTags.size() == 1) {
                PetData data = PetData.fromTag(petTags.get(0));
                Minecraft.getInstance().setScreen(
                        new com.arcadia.pets.client.PetRevealScreen(data, minRarity));
            } else {
                List<PetData> pets = new ArrayList<>(petTags.size());
                for (CompoundTag tag : petTags) pets.add(PetData.fromTag(tag));
                Minecraft.getInstance().setScreen(
                        new com.arcadia.pets.client.PetMultiRevealScreen(pets, minRarity));
            }
        });
    }
}
