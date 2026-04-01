package com.arcadia.pets.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → Client: tells the client to open the roster-selection screen for a duel.
 *
 * <p>Sends the player's full pet collection (as NBT tags) so the client can
 * render them for selection without further round-trips.</p>
 *
 * @param duelId       the UUID of the newly created {@link com.arcadia.pets.duel.DuelSession}
 * @param opponentName display name of the opponent
 * @param petTags      serialised {@link com.arcadia.pets.item.PetData} for each pet in the collection
 */
public record S2CDuelRosterPick(UUID duelId, String opponentName,
                                List<CompoundTag> petTags) implements CustomPacketPayload {

    public static final Type<S2CDuelRosterPick> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "duel_roster_pick"));

    public static final StreamCodec<FriendlyByteBuf, S2CDuelRosterPick> STREAM_CODEC =
            StreamCodec.of(S2CDuelRosterPick::encode, S2CDuelRosterPick::decode);

    private static void encode(FriendlyByteBuf buf, S2CDuelRosterPick pkt) {
        buf.writeUUID(pkt.duelId);
        buf.writeUtf(pkt.opponentName);
        buf.writeVarInt(pkt.petTags.size());
        for (CompoundTag tag : pkt.petTags) buf.writeNbt(tag);
    }

    private static S2CDuelRosterPick decode(FriendlyByteBuf buf) {
        UUID duelId       = buf.readUUID();
        String oppName    = buf.readUtf();
        int count         = buf.readVarInt();
        List<CompoundTag> tags = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CompoundTag t = buf.readNbt();
            if (t != null) tags.add(t);
        }
        return new S2CDuelRosterPick(duelId, oppName, tags);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new com.arcadia.pets.client.DuelRosterScreen(duelId, opponentName, petTags));
        });
    }
}
