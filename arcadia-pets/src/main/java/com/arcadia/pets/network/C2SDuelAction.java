package com.arcadia.pets.network;

import com.arcadia.pets.duel.DuelManager;
import com.arcadia.pets.duel.DuelPhase;
import com.arcadia.pets.duel.DuelSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client → Server: the player submits an action during their duel turn.
 *
 * <h3>Action types</h3>
 * <ul>
 *   <li>0 = ATTACK  — basic attack; {@code actorPetIdx} is which of your pets attacks,
 *       {@code targetPetIdx} is the enemy pet to hit</li>
 *   <li>1 = SKILL   — use a skill; {@code skillId} names the skill, {@code actorPetIdx} is your
 *       acting pet, {@code targetPetIdx} is the target (ally or enemy per skill type)</li>
 *   <li>2 = DEFEND  — that pet guards (GUARD status); {@code actorPetIdx} identifies the pet</li>
 *   <li>3 = FORFEIT — concede the duel (actorPetIdx/targetPetIdx ignored)</li>
 *   <li>4 = PASS    — end the entire player turn early, SP carried over</li>
 *   <li>5 = SKIP_PET — skip this specific pet's action this turn; {@code actorPetIdx} identifies the pet</li>
 *   <li>10 = ACCEPT_CHALLENGE  — sent by challenged player to accept a duel invite</li>
 *   <li>11 = DECLINE_CHALLENGE — sent by challenged player to decline</li>
 * </ul>
 */
public record C2SDuelAction(int actionType, String skillId,
                            int actorPetIdx, int targetPetIdx) implements CustomPacketPayload {

    public static final int ATTACK             = 0;
    public static final int SKILL              = 1;
    public static final int DEFEND             = 2;
    public static final int FORFEIT            = 3;
    public static final int PASS               = 4;
    public static final int SKIP_PET           = 5;
    public static final int ACCEPT_CHALLENGE   = 10;
    public static final int DECLINE_CHALLENGE  = 11;

    public static final Type<C2SDuelAction> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "duel_action"));

    public static final StreamCodec<FriendlyByteBuf, C2SDuelAction> STREAM_CODEC =
            StreamCodec.of(C2SDuelAction::encode, C2SDuelAction::decode);

    private static void encode(FriendlyByteBuf buf, C2SDuelAction pkt) {
        buf.writeVarInt(pkt.actionType);
        buf.writeUtf(pkt.skillId);
        buf.writeVarInt(pkt.actorPetIdx);
        buf.writeVarInt(pkt.targetPetIdx);
    }

    private static C2SDuelAction decode(FriendlyByteBuf buf) {
        return new C2SDuelAction(buf.readVarInt(), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(C2SDuelAction pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            switch (pkt.actionType) {
                case ACCEPT_CHALLENGE  -> handleAccept(sp);
                case DECLINE_CHALLENGE -> handleDecline(sp);
                default                -> handleCombatAction(sp, pkt);
            }
        });
    }

    // ── Challenge response ────────────────────────────────────────────────────

    private static void handleAccept(ServerPlayer target) {
        UUID challengerUuid = DuelManager.getChallengerFor(target.getUUID());
        if (challengerUuid == null) return;

        ServerPlayer challenger = (ServerPlayer) target.getServer()
                .getPlayerList().getPlayer(challengerUuid);
        if (challenger == null) {
            DuelManager.clearChallenge(target.getUUID());
            return;
        }

        DuelSession session = DuelManager.accept(challenger, target);

        // Send roster-pick packet to both players
        sendRosterPick(challenger, target, session);
        sendRosterPick(target, challenger, session);
    }

    private static void sendRosterPick(ServerPlayer player, ServerPlayer opponent,
                                        DuelSession session) {
        var col = com.arcadia.pets.server.PetCollectionSavedData.getOrCreate(player.getServer());
        var collection = col.getCollection(player.getUUID());

        java.util.List<net.minecraft.nbt.CompoundTag> tags = new java.util.ArrayList<>();
        for (net.minecraft.world.item.ItemStack stack : collection) {
            com.arcadia.pets.item.PetData pd =
                    com.arcadia.pets.item.PetData.fromStack(stack);
            if (pd != null) tags.add(pd.toTag());
        }

        PacketDistributor.sendToPlayer(player,
                new S2CDuelRosterPick(session.duelId, opponent.getName().getString(), tags));
    }

    private static void handleDecline(ServerPlayer target) {
        UUID challengerUuid = DuelManager.getChallengerFor(target.getUUID());
        DuelManager.clearChallenge(target.getUUID());
        if (challengerUuid == null) return;

        ServerPlayer challenger = (ServerPlayer) target.getServer()
                .getPlayerList().getPlayer(challengerUuid);
        if (challenger != null) {
            challenger.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e[Duel] §7" + target.getName().getString() + " declined your challenge."));
        }
    }

    // ── In-combat action ─────────────────────────────────────────────────────

    private static void handleCombatAction(ServerPlayer player, C2SDuelAction pkt) {
        // Grab session BEFORE handleAction — forfeit removes it from the active map
        DuelSession session = DuelManager.getSessionFor(player.getUUID());
        if (session == null) return;

        DuelManager.handleAction(player, pkt.actionType, pkt.skillId,
                pkt.actorPetIdx, pkt.targetPetIdx);

        // Broadcast updated state to both players
        S2CDuelState state = S2CDuelState.from(session);
        ServerPlayer p1 = (ServerPlayer) player.getServer()
                .getPlayerList().getPlayer(session.p1);
        ServerPlayer p2 = (ServerPlayer) player.getServer()
                .getPlayerList().getPlayer(session.p2);
        if (p1 != null) PacketDistributor.sendToPlayer(p1, state);
        if (p2 != null) PacketDistributor.sendToPlayer(p2, state);

        // If the duel just ended, distribute rewards
        if (session.phase == DuelPhase.FINISHED && session.winner != null) {
            grantRewards(player.getServer(), session);
        }
    }

    // ── Reward distribution ───────────────────────────────────────────────────

    private static void grantRewards(net.minecraft.server.MinecraftServer server,
                                      DuelSession session) {
        UUID loser = session.opponentOf(session.winner);
        ServerPlayer winner = (ServerPlayer) server.getPlayerList().getPlayer(session.winner);
        if (winner == null) return;

        int essence = DuelManager.essenceRewardFor(session, loser);
        int coins   = DuelManager.coinsRewardFor(session, loser);

        // Give Star Essence
        net.minecraft.world.item.ItemStack essenceStack =
                new net.minecraft.world.item.ItemStack(
                        com.arcadia.pets.PetsModItems.STAR_ESSENCE.get(), essence);
        winner.getInventory().add(essenceStack);

        // Give Numismatics coins if the mod is available
        if (net.neoforged.fml.ModList.get().isLoaded("numismatics")) {
            tryGiveCoins(winner, coins);
        }

        winner.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§6[Duel] §aVictory! Rewards: §e" + essence + "x Star Essence"
                        + (net.neoforged.fml.ModList.get().isLoaded("numismatics")
                        ? " + " + coins + " coins" : "") + "."));

        // Quest progress: PET_DUEL_WIN
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new com.arcadia.lib.event.QuestProgressEvent(
                        winner.getUUID(), "PET_DUEL_WIN", "", 1));

        // Duel participation XP: 2 XP per roster pet for winner, 1 for loser
        ServerPlayer loserPlayer = (ServerPlayer) server.getPlayerList().getPlayer(loser);
        grantDuelParticipationXp(winner,      session.rosterFor(session.winner), 2);
        grantDuelParticipationXp(loserPlayer, session.rosterFor(loser),          1);

        // ELO update
        String winnerMob = mobTypeOf(session.rosterFor(session.winner));
        String loserMob  = mobTypeOf(session.rosterFor(loser));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new com.arcadia.lib.event.DuelResultEvent(
                        session.winner, loser, winnerMob, loserMob,
                        petIdsOf(session.rosterFor(session.winner)),
                        petIdsOf(session.rosterFor(loser))));
    }

    private static void grantDuelParticipationXp(ServerPlayer player,
                                                  com.arcadia.pets.item.PetData[] roster,
                                                  int xpPerPet) {
        if (player == null) return;
        for (com.arcadia.pets.item.PetData pd : roster) {
            if (pd == null) continue;
            com.arcadia.pets.server.PetManager.addSkillXpToPet(player, pd.petId(), xpPerPet);
        }
    }

    private static String mobTypeOf(com.arcadia.pets.item.PetData[] roster) {
        for (com.arcadia.pets.item.PetData pd : roster) {
            if (pd != null) return pd.mobType();
        }
        return "";
    }

    private static java.util.List<java.util.UUID> petIdsOf(com.arcadia.pets.item.PetData[] roster) {
        java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
        for (com.arcadia.pets.item.PetData pd : roster) {
            if (pd != null) ids.add(pd.petId());
        }
        return ids;
    }

    private static void tryGiveCoins(ServerPlayer player, int amount) {
        try {
            Class<?> api = Class.forName("com.simibubi.numismatics.api.NumismaticsApi");
            var method = api.getMethod("addCoins", ServerPlayer.class, int.class);
            method.invoke(null, player, amount);
        } catch (Exception ignored) {}
    }
}
