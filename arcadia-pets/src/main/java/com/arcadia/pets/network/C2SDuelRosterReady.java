package com.arcadia.pets.network;

import com.arcadia.pets.duel.DuelManager;
import com.arcadia.pets.duel.DuelPhase;
import com.arcadia.pets.duel.DuelSession;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetRarity;
import com.arcadia.pets.server.PetCollectionSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client → Server: player confirms their roster selection.
 *
 * <p>Contains the duel UUID and up to 3 pet UUIDs from the player's collection
 * (in desired slot order 0→1→2).</p>
 */
public record C2SDuelRosterReady(UUID duelId, List<UUID> petIds) implements CustomPacketPayload {

    public static final Type<C2SDuelRosterReady> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "duel_roster_ready"));

    public static final StreamCodec<FriendlyByteBuf, C2SDuelRosterReady> STREAM_CODEC =
            StreamCodec.of(C2SDuelRosterReady::encode, C2SDuelRosterReady::decode);

    private static void encode(FriendlyByteBuf buf, C2SDuelRosterReady pkt) {
        buf.writeUUID(pkt.duelId);
        buf.writeVarInt(pkt.petIds.size());
        for (UUID id : pkt.petIds) buf.writeUUID(id);
    }

    private static C2SDuelRosterReady decode(FriendlyByteBuf buf) {
        UUID duelId = buf.readUUID();
        int count   = buf.readVarInt();
        List<UUID> ids = new ArrayList<>(count);
        for (int i = 0; i < Math.min(count, 3); i++) ids.add(buf.readUUID());
        return new C2SDuelRosterReady(duelId, ids);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(C2SDuelRosterReady pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            DuelSession session = DuelManager.confirmRoster(sp, pkt.duelId, pkt.petIds);
            if (session == null) return;

            // If combat has started (both confirmed), send initial S2CDuelState to both
            if (session.phase == DuelPhase.ACTIVE) {
                S2CDuelState state = S2CDuelState.from(session);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        (ServerPlayer) sp.getServer().getPlayerList().getPlayer(session.p1), state);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        (ServerPlayer) sp.getServer().getPlayerList().getPlayer(session.p2), state);
            }
        });
    }
}
