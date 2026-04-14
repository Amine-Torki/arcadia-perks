package com.arcadia.pets.network;

import com.arcadia.pets.duel.DuelManager;
import com.arcadia.pets.duel.DuelSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: player sends a non-binding "intent" hint during their turn.
 * The server rebroadcasts it as {@link S2CDuelHint} to both players so the
 * opponent can see what their counterpart is doing in real time.
 *
 * <h3>Hint types</h3>
 * <ul>
 *   <li>{@link #PET_SELECTED}      — player clicked a pending pet card ({@code petIdx})</li>
 *   <li>{@link #ATTACK_TARGETING}  — player is picking an enemy target to attack</li>
 *   <li>{@link #SKILL_TARGETING_E} — player is picking an enemy target for a skill</li>
 *   <li>{@link #SKILL_TARGETING_A} — player is picking an ally target for a skill</li>
 *   <li>{@link #CLEARED}           — selection cleared / action sent ({@code petIdx} ignored)</li>
 * </ul>
 */
public record C2SDuelHint(int hintType, int petIdx) implements CustomPacketPayload {

    public static final int PET_SELECTED      = 0;
    public static final int ATTACK_TARGETING  = 1;
    public static final int SKILL_TARGETING_E = 2;
    public static final int SKILL_TARGETING_A = 3;
    public static final int CLEARED           = 4;

    public static final Type<C2SDuelHint> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "duel_hint_c2s"));

    public static final StreamCodec<FriendlyByteBuf, C2SDuelHint> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> { buf.writeVarInt(pkt.hintType); buf.writeVarInt(pkt.petIdx); },
                    buf -> new C2SDuelHint(buf.readVarInt(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(C2SDuelHint pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            DuelSession session = DuelManager.getSessionFor(sp.getUUID());
            if (session == null) return;

            S2CDuelHint hint = new S2CDuelHint(sp.getUUID(), pkt.hintType(), pkt.petIdx());
            // Broadcast to both players (receiver side ignores its own UUID)
            ServerPlayer p1 = sp.getServer().getPlayerList().getPlayer(session.p1);
            ServerPlayer p2 = sp.getServer().getPlayerList().getPlayer(session.p2);
            if (p1 != null) PacketDistributor.sendToPlayer(p1, hint);
            if (p2 != null) PacketDistributor.sendToPlayer(p2, hint);
        });
    }
}
