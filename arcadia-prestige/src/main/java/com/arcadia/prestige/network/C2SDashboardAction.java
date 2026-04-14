package com.arcadia.prestige.network;

import com.arcadia.lib.ArcadiaModRegistry;
import com.arcadia.lib.data.DatabaseManager;
import com.arcadia.lib.data.PlayerDataHandler;
import com.arcadia.lib.permissions.PermissionService;
import com.arcadia.prestige.server.DailyRewardHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import com.arcadia.prestige.quest.QuestManager;
public record C2SDashboardAction(int actionId, String payload) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int SELECT_PARTICLE = 0;
    public static final int SUMMON_PET = 1;
    public static final int UNSUMMON_PET = 2;
    public static final int FEED_PET = 3;
    public static final int CLAIM_DAILY = 4;
    public static final int SWITCH_TAB = 5;
    public static final int APPLY_STAR_ESSENCE = 6;
    public static final int OPEN_TAB = 7;
    /** Claim a completed daily quest (payload = quest index "0"/"1"/"2"). */
    public static final int CLAIM_QUEST = 8;

    public static final Type<C2SDashboardAction> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_prestige", "dashboard_action"));

    public static final StreamCodec<FriendlyByteBuf, C2SDashboardAction> STREAM_CODEC =
            StreamCodec.of(C2SDashboardAction::encode, C2SDashboardAction::decode);

    private static void encode(FriendlyByteBuf buf, C2SDashboardAction pkt) {
        buf.writeVarInt(pkt.actionId);
        buf.writeUtf(pkt.payload);
    }

    private static C2SDashboardAction decode(FriendlyByteBuf buf) {
        return new C2SDashboardAction(buf.readVarInt(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;

            switch (actionId) {
                case SELECT_PARTICLE -> handleSelectParticle(serverPlayer, payload);
                case SUMMON_PET      -> ArcadiaModRegistry.executeServerAction("pets.summon:" + payload, serverPlayer);
                case UNSUMMON_PET    -> ArcadiaModRegistry.executeServerAction("pets.despawn", serverPlayer);
                case FEED_PET        -> { /* Reserved */ }
                case CLAIM_DAILY     -> {
                    DailyRewardHandler.tryClaim(serverPlayer);
                    QuestManager.trackProgress(serverPlayer.getUUID(), "DAILY_CLAIM", "", 1);
                }
                case CLAIM_QUEST     -> handleClaimQuest(serverPlayer, payload);
                case SWITCH_TAB      -> handleSwitchTab(serverPlayer, payload);
                case APPLY_STAR_ESSENCE -> ArcadiaModRegistry.executeServerAction("pets.star_essence:" + payload, serverPlayer);
                case OPEN_TAB        -> handleOpenTab(serverPlayer, payload);
                default -> LOGGER.warn("Unknown dashboard action {} from {}", actionId, serverPlayer.getName().getString());
            }
        });
    }

    private static void handleSelectParticle(ServerPlayer player, String particleId) {
        if (!PermissionService.canUseCosmetic(player, particleId)) return;
        DatabaseManager.executeAsync(() -> PlayerDataHandler.saveParticle(player.getUUID(), particleId));
        PacketHandler.sendToAll(player.getServer(), new S2CParticleSync(player.getUUID(), particleId));
    }

    private static void handleSwitchTab(ServerPlayer player, String tabPayload) {
        if (player.containerMenu instanceof com.arcadia.prestige.server.DashboardMenu menu) {
            try { menu.switchTab(Integer.parseInt(tabPayload), player); }
            catch (NumberFormatException ignored) {}
        }
    }

    private static void handleOpenTab(ServerPlayer player, String tabStr) {
        try {
            int tab = Integer.parseInt(tabStr);
            // Refresh cache for AH tab via server action (no direct import of AuctionManager)
            if (tab == 3) ArcadiaModRegistry.executeServerAction("ah.refresh_cache", player);
            com.arcadia.prestige.server.DashboardMenu.openFor(player, tab);
        } catch (NumberFormatException e) {
            com.arcadia.prestige.server.DashboardMenu.openFor(player);
        }
    }

    private static void handleClaimQuest(ServerPlayer player, String questIndexStr) {
        try {
            int idx = Integer.parseInt(questIndexStr);
            QuestManager.claimQuest(player, QuestManager.todayKey(), idx);
            if (player.containerMenu instanceof com.arcadia.prestige.server.DashboardMenu menu) {
                menu.switchTab(4, player);
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid quest index from {}: {}", player.getName().getString(), questIndexStr);
        }
    }
}
