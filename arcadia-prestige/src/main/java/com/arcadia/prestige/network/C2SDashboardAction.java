package com.arcadia.prestige.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import com.arcadia.lib.data.DatabaseManager;
import com.arcadia.lib.data.PlayerDataHandler;
import com.arcadia.prestige.quest.QuestManager;
import com.arcadia.prestige.server.DailyRewardHandler;
import com.arcadia.prestige.server.DashboardMenu;
import com.arcadia.prestige.server.LuckPermsHook;
import com.arcadia.pets.server.PetManager;

public record C2SDashboardAction(int actionId, String payload) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int SELECT_PARTICLE = 0;
    public static final int SUMMON_PET = 1;
    public static final int UNSUMMON_PET = 2;
    public static final int FEED_PET = 3;
    public static final int CLAIM_DAILY = 4;
    public static final int SWITCH_TAB = 5;
    public static final int APPLY_STAR_ESSENCE = 6;
    /** Open the dashboard at a specific tab (payload = tab index as string). */
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
        int actionId = buf.readVarInt();
        String payload = buf.readUtf();
        return new C2SDashboardAction(actionId, payload);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            switch (actionId) {
                case SELECT_PARTICLE -> handleSelectParticle(serverPlayer, payload);
                case SUMMON_PET -> handleSummonPet(serverPlayer, payload);
                case UNSUMMON_PET -> PetManager.despawn(serverPlayer);
                case FEED_PET -> { /* Reserved for collectible system */ }
                case CLAIM_DAILY -> {
                    DailyRewardHandler.tryClaim(serverPlayer);
                    // Track DAILY_CLAIM quest progress
                    QuestManager.trackProgress(serverPlayer.getUUID(), "DAILY_CLAIM", "", 1);
                }
                case CLAIM_QUEST -> handleClaimQuest(serverPlayer, payload);
                case SWITCH_TAB -> handleSwitchTab(serverPlayer, payload);
                case APPLY_STAR_ESSENCE -> handleApplyStarEssence(serverPlayer, payload);
                case OPEN_TAB -> handleOpenTab(serverPlayer, payload);
                default -> LOGGER.warn("Unknown dashboard action {} from {}", actionId, serverPlayer.getName().getString());
            }
        });
    }

    private static void handleSelectParticle(ServerPlayer player, String particleId) {
        if (!LuckPermsHook.canUseParticle(player, particleId)) {
            LOGGER.debug("Player {} does not have permission for particle {}", player.getName().getString(), particleId);
            return;
        }

        DatabaseManager.executeAsync(() -> {
            PlayerDataHandler.saveParticle(player.getUUID(), particleId);
        });

        PacketHandler.sendToAll(
                player.getServer(),
                new S2CParticleSync(player.getUUID(), particleId)
        );
    }

    private static void handleSummonPet(ServerPlayer player, String slotPayload) {
        try {
            int slot = Integer.parseInt(slotPayload);
            PetManager.summonFromInventory(player, slot);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid pet slot from {}: {}", player.getName().getString(), slotPayload);
        }
    }

    private static void handleSwitchTab(ServerPlayer player, String tabPayload) {
        if (player.containerMenu instanceof com.arcadia.prestige.server.DashboardMenu menu) {
            try {
                int tab = Integer.parseInt(tabPayload);
                menu.switchTab(tab, player);
            } catch (NumberFormatException ignored) {}
        }
    }

    private static void handleOpenTab(ServerPlayer player, String tabStr) {
        try {
            int tab = Integer.parseInt(tabStr);
            if (tab == 3) com.arcadia.ah.auction.AuctionManager.refreshCache();
            com.arcadia.prestige.server.DashboardMenu.openFor(player, tab);
        } catch (NumberFormatException e) {
            com.arcadia.prestige.server.DashboardMenu.openFor(player);
        }
    }

    private static void handleClaimQuest(ServerPlayer player, String questIndexStr) {
        try {
            int idx = Integer.parseInt(questIndexStr);
            QuestManager.claimQuest(player, QuestManager.todayKey(), idx);
            // Refresh the quest tab if the dashboard is open
            if (player.containerMenu instanceof DashboardMenu menu) {
                menu.switchTab(4, player);
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid quest index from {}: {}", player.getName().getString(), questIndexStr);
        }
    }

    private static void handleApplyStarEssence(ServerPlayer player, String slotPayload) {
        try {
            int petSlot = Integer.parseInt(slotPayload);
            PetManager.applyStarEssence(player, petSlot);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid pet slot for star essence from {}: {}", player.getName().getString(), slotPayload);
        }
    }
}
