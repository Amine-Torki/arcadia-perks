package com.arcadia.ah.server;

import com.arcadia.ah.AhModMenus;
import com.arcadia.ah.auction.AhLeaderboardEntry;
import com.arcadia.ah.auction.AuctionDatabase;
import com.arcadia.ah.auction.NumismaticsCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only 6-row chest showing the top 10 AH sellers.
 * Ranking: unique buyers DESC → median deal size DESC.
 */
public class AhLeaderboardMenu extends AbstractContainerMenu {

    private static final int BACK_SLOT = 49;
    private final SimpleContainer container = new SimpleContainer(54);

    public AhLeaderboardMenu(int syncId, Inventory playerInv) {
        super(AhModMenus.AH_LEADERBOARD_MENU.get(), syncId);
        // 54 display-only slots (6-row chest area)
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18) {
                    @Override public boolean mayPlace(ItemStack s) { return false; }
                    @Override public boolean mayPickup(Player p) { return false; }
                });
            }
        }
        // Player inventory (rows 1-3)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }
        if (!playerInv.player.level().isClientSide()) build();
    }

    private void build() {
        // Fill all with dark glass
        ItemStack bg = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        setName(bg, Component.literal(" "));
        for (int i = 0; i < 54; i++) container.setItem(i, bg.copy());

        // Title item at slot 4
        ItemStack title = new ItemStack(Items.NETHER_STAR);
        setName(title, Component.literal("⭐ Top Business").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        setLore(title, List.of(
                Component.literal("Ranked by unique clients").withStyle(ChatFormatting.YELLOW),
                Component.literal("Tiebreaker: median deal size").withStyle(ChatFormatting.GRAY),
                Component.literal(" "),
                Component.literal("§8Farming-resistant: only distinct").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("§8buyers contribute to your rank.").withStyle(ChatFormatting.DARK_GRAY)
        ));
        container.setItem(4, title);

        // Fetch leaderboard (called server-side — runs synchronously here since we're
        // already on the server thread; DB call is fast for top-10)
        List<AhLeaderboardEntry> entries = AuctionDatabase.fetchLeaderboard(10);

        // Rank items placed in two columns for visual clarity:
        // Ranks 1-5: col 1 (slots 9, 18, 27, 36, 45)
        // Ranks 6-10: col 5 (slots 13, 22, 31, 40, 49)
        // But simpler: just place them in a single column with the main info
        // Slots 9, 11, 13, 15, 17, 19, 21, 23, 25, 27 — row 1+2, odd cols with spacing

        // Actually simplest: 2 columns of 5, left at col1, right at col5
            int[] slots = {10, 19, 28, 37, 46, 14, 23, 32, 41, 50};

        for (int i = 0; i < entries.size(); i++) {
            AhLeaderboardEntry e = entries.get(i);
            int rank = i + 1;

            ItemStack icon = rankIcon(rank);
            setName(icon, Component.literal(rankPrefix(rank) + e.sellerName())
                    .withStyle(rank == 1 ? ChatFormatting.GOLD
                            : rank <= 3 ? ChatFormatting.YELLOW
                            : ChatFormatting.WHITE));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§6Unique clients: §f" + e.uniqueBuyers()));
            lore.add(Component.literal("§6Median deal:    §f" + NumismaticsCompat.formatPrice(e.medianAmountPerBuyer())));
            lore.add(Component.literal("§7Total sales:    §f" + e.totalSales()));
            lore.add(Component.literal("§7Total revenue:  §8" + NumismaticsCompat.formatPrice(e.totalRevenue())));
            setLore(icon, lore);

            if (rank == 1) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            container.setItem(slots[i], icon);
        }

        if (entries.isEmpty()) {
            ItemStack empty = new ItemStack(Items.BARRIER);
            setName(empty, Component.literal("No sales recorded yet").withStyle(ChatFormatting.GRAY));
            container.setItem(22, empty);
        }

        ItemStack back = new ItemStack(Items.ARROW);
        setName(back, Component.literal("← Back to Auction House").withStyle(ChatFormatting.YELLOW));
        container.setItem(BACK_SLOT, back);
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        if (slotId == BACK_SLOT && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            AhDashboardBridge.openAhTab(sp);
            return;
        }
        broadcastChanges();
    }

    private static ItemStack rankIcon(int rank) {
        return new ItemStack(switch (rank) {
            case 1  -> Items.NETHER_STAR;
            case 2, 3 -> Items.GOLD_INGOT;
            default -> Items.GOLD_NUGGET;
        });
    }

    private static String rankPrefix(int rank) {
        return switch (rank) {
            case 1 -> "§6#1 §l";
            case 2 -> "§e#2 ";
            case 3 -> "§e#3 ";
            default -> "§7#" + rank + " ";
        };
    }

    private void setName(ItemStack s, Component n) { s.set(DataComponents.CUSTOM_NAME, n); }
    private void setLore(ItemStack s, List<Component> l) { s.set(DataComponents.LORE, new ItemLore(l)); }

    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) { return true; }

    public static void openFor(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new AhLeaderboardMenu(id, inv),
                Component.literal("AH Leaderboard")
        ));
    }
}
