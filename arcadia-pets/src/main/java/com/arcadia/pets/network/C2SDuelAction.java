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
 *   <li>0 = ATTACK — basic attack; {@code targetPetIdx} is the enemy pet to hit</li>
 *   <li>1 = SKILL  — use a skill; {@code skillId} names the skill, {@code targetPetIdx}
 *       indicates the target pet (ally or enemy, per the skill's target type)</li>
 *   <li>2 = DEFEND — brace for impact; ends the turn</li>
 *   <li>3 = FORFEIT — concede the duel</li>
 *   <li>4 = PASS   — spend remaining AP and end the turn</li>
 *   <li>10 = ACCEPT_CHALLENGE — sent by the challenged player to accept a duel invite</li>
 *   <li>11 = DECLINE_CHALLENGE — sent by the challenged player to decline</li>
 * </ul>
 */
public record C2SDuelAction(int actionType, String skillId,
                            int targetPetIdx) implements CustomPacketPayload {

    public static final int ATTACK             = 0;
    public static final int SKILL              = 1;
    public static final int DEFEND             = 2;
    public static final int FORFEIT            = 3;
    public static final int PASS               = 4;
    public static final int ACCEPT_CHALLENGE   = 10;
    public static final int DECLINE_CHALLENGE  = 11;

    public static final Type<C2SDuelAction> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "duel_action"));

    public static final StreamCodec<FriendlyByteBuf, C2SDuelAction> STREAM_CODEC =
            StreamCodec.of(C2SDuelAction::encode, C2SDuelAction::decode);

    private static void encode(FriendlyByteBuf buf, C2SDuelAction pkt) {
        buf.writeVarInt(pkt.actionType);
        buf.writeUtf(pkt.skillId);
        buf.writeVarInt(pkt.targetPetIdx);
    }

    private static C2SDuelAction decode(FriendlyByteBuf buf) {
        return new C2SDuelAction(buf.readVarInt(), buf.readUtf(), buf.readVarInt());
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
        DuelManager.ActionResult result = DuelManager.handleAction(
                player, pkt.actionType, pkt.skillId, pkt.targetPetIdx);

        DuelSession session = DuelManager.getSessionFor(player.getUUID());
        if (session == null) return;

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
    }

    private static void tryGiveCoins(ServerPlayer player, int amount) {
        try {
            // Numismatics API call — wrapped in try/catch in case the API changes
            Class<?> api = Class.forName("com.simibubi.numismatics.api.NumismaticsApi");
            var method = api.getMethod("addCoins", ServerPlayer.class, int.class);
            method.invoke(null, player, amount);
        } catch (Exception ignored) {
            // Numismatics not present or API mismatch — silently skip coins
        }
    }
}
