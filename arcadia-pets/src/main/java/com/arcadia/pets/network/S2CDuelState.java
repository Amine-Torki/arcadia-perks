package com.arcadia.pets.network;

import com.arcadia.pets.client.DuelClientState;
import com.arcadia.pets.duel.ActiveEffect;
import com.arcadia.pets.duel.DuelPhase;
import com.arcadia.pets.duel.DuelSession;
import com.arcadia.pets.duel.TurnSlot;
import com.arcadia.pets.item.PetData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

/**
 * Server → Client: full duel state synchronisation.
 *
 * <p>Sent whenever the game state changes (after each action, turn start/end,
 * or on initial combat start). The client reconstructs its display from this snapshot.</p>
 */
public record S2CDuelState(
        UUID   duelId,
        UUID   p1, UUID p2,
        // Rosters as serialised NBT (sent once; clients cache them after first receipt)
        CompoundTag[] p1RosterTags,
        CompoundTag[] p2RosterTags,
        // Live HP & max-HP per pet (3 values each side)
        int[] p1Hp, int[] p1MaxHp,
        int[] p2Hp, int[] p2MaxHp,
        // Current turn: who is acting and with how many SP
        UUID   actorUuid,
        int    actorPetIdx,
        int    currentSP,
        // Encoded turn order (UUID + petIdx pairs for UI display)
        List<long[]>  turnOrderEncoded,  // each entry: [uuidMsb, uuidLsb, petIdx]
        // Skill cooldowns for BOTH sides (key = "side_petIdx_skillId", value = turns remaining)
        Map<String, Integer> skillCooldowns,
        // Active effects per pet slot (key = "side_petIdx")
        Map<String, List<String>> effectLabels,
        // Combat log lines (up to 24)
        List<String> combatLog,
        // Phase & result
        int    phaseOrdinal,
        UUID   winner,          // null unless finished
        // Deadline (epoch ms) so client can show a countdown
        long   actionDeadline
) implements CustomPacketPayload {

    public static final Type<S2CDuelState> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "duel_state"));

    public static final StreamCodec<FriendlyByteBuf, S2CDuelState> STREAM_CODEC =
            StreamCodec.of(S2CDuelState::encode, S2CDuelState::decode);

    // ── Factory ───────────────────────────────────────────────────────────────

    /** Serialises a live {@link DuelSession} into a packet. */
    public static S2CDuelState from(DuelSession session) {
        CompoundTag[] p1Tags = rosterTags(session.p1Roster);
        CompoundTag[] p2Tags = rosterTags(session.p2Roster);

        TurnSlot slot = session.currentSlot();
        UUID actorUuid   = slot != null ? slot.ownerUuid() : null;
        int  actorPetIdx = slot != null ? slot.petIndex()  : 0;

        // Encode turn order as longs for compactness
        List<long[]> orderEnc = new ArrayList<>();
        for (TurnSlot s : session.getTurnOrder()) {
            orderEnc.add(new long[]{
                    s.ownerUuid().getMostSignificantBits(),
                    s.ownerUuid().getLeastSignificantBits(),
                    s.petIndex()});
        }

        // Encode effect labels per slot
        Map<String, List<String>> effectLabels = new LinkedHashMap<>();
        session.getAllEffects().forEach((key, effects) -> {
            List<String> labels = new ArrayList<>();
            for (ActiveEffect e : effects) labels.add(e.label());
            if (!labels.isEmpty()) effectLabels.put(key, labels);
        });

        return new S2CDuelState(
                session.duelId,
                session.p1, session.p2,
                p1Tags, p2Tags,
                session.p1Hp.clone(), session.p1MaxHp.clone(),
                session.p2Hp.clone(), session.p2MaxHp.clone(),
                actorUuid, actorPetIdx, session.currentSP,
                orderEnc,
                new LinkedHashMap<>(session.getAllCooldowns()),
                effectLabels,
                session.getLog(),
                session.phase.ordinal(),
                session.winner,
                session.actionDeadline);
    }

    private static CompoundTag[] rosterTags(PetData[] roster) {
        CompoundTag[] tags = new CompoundTag[3];
        for (int i = 0; i < 3; i++) {
            tags[i] = roster[i] != null ? roster[i].toTag() : new CompoundTag();
        }
        return tags;
    }

    // ── Encode / decode ───────────────────────────────────────────────────────

    private static void encode(FriendlyByteBuf buf, S2CDuelState pkt) {
        buf.writeUUID(pkt.duelId);
        buf.writeUUID(pkt.p1);
        buf.writeUUID(pkt.p2);

        writeTagArray(buf, pkt.p1RosterTags);
        writeTagArray(buf, pkt.p2RosterTags);

        writeIntArray(buf, pkt.p1Hp);
        writeIntArray(buf, pkt.p1MaxHp);
        writeIntArray(buf, pkt.p2Hp);
        writeIntArray(buf, pkt.p2MaxHp);

        buf.writeBoolean(pkt.actorUuid != null);
        if (pkt.actorUuid != null) buf.writeUUID(pkt.actorUuid);
        buf.writeVarInt(pkt.actorPetIdx);
        buf.writeVarInt(pkt.currentSP);

        buf.writeVarInt(pkt.turnOrderEncoded.size());
        for (long[] e : pkt.turnOrderEncoded) {
            buf.writeLong(e[0]); buf.writeLong(e[1]); buf.writeVarInt((int) e[2]);
        }

        buf.writeVarInt(pkt.skillCooldowns.size());
        pkt.skillCooldowns.forEach((k, v) -> { buf.writeUtf(k); buf.writeVarInt(v); });

        buf.writeVarInt(pkt.effectLabels.size());
        pkt.effectLabels.forEach((k, labels) -> {
            buf.writeUtf(k);
            buf.writeVarInt(labels.size());
            labels.forEach(buf::writeUtf);
        });

        buf.writeVarInt(pkt.combatLog.size());
        pkt.combatLog.forEach(buf::writeUtf);

        buf.writeVarInt(pkt.phaseOrdinal);
        buf.writeBoolean(pkt.winner != null);
        if (pkt.winner != null) buf.writeUUID(pkt.winner);
        buf.writeLong(pkt.actionDeadline);
    }

    private static S2CDuelState decode(FriendlyByteBuf buf) {
        UUID duelId = buf.readUUID();
        UUID p1 = buf.readUUID(), p2 = buf.readUUID();

        CompoundTag[] p1Tags = readTagArray(buf, 3);
        CompoundTag[] p2Tags = readTagArray(buf, 3);
        int[] p1Hp = readIntArray(buf, 3), p1MaxHp = readIntArray(buf, 3);
        int[] p2Hp = readIntArray(buf, 3), p2MaxHp = readIntArray(buf, 3);

        UUID actor = buf.readBoolean() ? buf.readUUID() : null;
        int actorPetIdx = buf.readVarInt(), ap = buf.readVarInt();

        int orderSz = buf.readVarInt();
        List<long[]> order = new ArrayList<>(orderSz);
        for (int i = 0; i < orderSz; i++)
            order.add(new long[]{buf.readLong(), buf.readLong(), buf.readVarInt()});

        int cdSz = buf.readVarInt();
        Map<String, Integer> cds = new LinkedHashMap<>(cdSz);
        for (int i = 0; i < cdSz; i++) cds.put(buf.readUtf(), buf.readVarInt());

        int fxSz = buf.readVarInt();
        Map<String, List<String>> fxLabels = new LinkedHashMap<>(fxSz);
        for (int i = 0; i < fxSz; i++) {
            String key = buf.readUtf();
            int sz = buf.readVarInt();
            List<String> ls = new ArrayList<>(sz);
            for (int j = 0; j < sz; j++) ls.add(buf.readUtf());
            fxLabels.put(key, ls);
        }

        int logSz = buf.readVarInt();
        List<String> log = new ArrayList<>(logSz);
        for (int i = 0; i < logSz; i++) log.add(buf.readUtf());

        int phase = buf.readVarInt();
        UUID winner = buf.readBoolean() ? buf.readUUID() : null;
        long deadline = buf.readLong();

        return new S2CDuelState(duelId, p1, p2, p1Tags, p2Tags,
                p1Hp, p1MaxHp, p2Hp, p2MaxHp,
                actor, actorPetIdx, ap, order, cds, fxLabels, log,
                phase, winner, deadline);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void writeTagArray(FriendlyByteBuf buf, CompoundTag[] tags) {
        buf.writeVarInt(tags.length);
        for (CompoundTag t : tags) buf.writeNbt(t);
    }

    private static CompoundTag[] readTagArray(FriendlyByteBuf buf, int expected) {
        int sz = buf.readVarInt();
        CompoundTag[] tags = new CompoundTag[sz];
        for (int i = 0; i < sz; i++) {
            tags[i] = buf.readNbt();
            if (tags[i] == null) tags[i] = new CompoundTag();
        }
        return tags;
    }

    private static void writeIntArray(FriendlyByteBuf buf, int[] arr) {
        buf.writeVarInt(arr.length);
        for (int v : arr) buf.writeVarInt(v);
    }

    private static int[] readIntArray(FriendlyByteBuf buf, int expected) {
        int sz = buf.readVarInt();
        int[] arr = new int[sz];
        for (int i = 0; i < sz; i++) arr[i] = buf.readVarInt();
        return arr;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            DuelClientState.update(this);
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!(mc.screen instanceof com.arcadia.pets.client.DuelScreen)) {
                mc.setScreen(new com.arcadia.pets.client.DuelScreen());
            }
        });
    }
}
