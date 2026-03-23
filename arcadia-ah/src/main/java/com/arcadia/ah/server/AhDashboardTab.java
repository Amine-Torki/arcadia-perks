package com.arcadia.ah.server;

import com.arcadia.ah.auction.AuctionItemSerializer;
import com.arcadia.ah.auction.AuctionListing;
import com.arcadia.ah.auction.AuctionManager;
import com.arcadia.ah.auction.NumismaticsCompat;
import com.arcadia.ah.network.S2COpenAhSearch;
import com.arcadia.lib.dashboard.DashboardTabHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements all Auction House tab (tab 3) logic for the Arcadia Dashboard.
 * Registered into DashboardMenu at startup when arcadia-ah is present.
 */
public final class AhDashboardTab implements DashboardTabHandler {

    private int ahPage = 0;
    private String ahCategory = "";
    private boolean ahMyListings = false;

    // ── DashboardTabHandler ───────────────────────────────────────────────────

    @Override
    public void buildTab(SimpleContainer container, ServerPlayer player) {
        List<AuctionListing> filtered = getFiltered(player);
        int total   = filtered.size();
        int maxPage = total == 0 ? 0 : (total - 1) / 36;
        if (ahPage > maxPage) ahPage = maxPage;

        for (int i = 0; i < 36; i++) {
            int slot    = 9 + i;
            int listIdx = ahPage * 36 + i;
            if (listIdx < total) {
                AuctionListing listing = filtered.get(listIdx);
                net.minecraft.core.HolderLookup.Provider reg = player.getServer().registryAccess();
                ItemStack item = AuctionItemSerializer.fromBase64(listing.itemNbt(), reg);
                if (item.isEmpty()) {
                    item = new ItemStack(Items.BARRIER);
                    setName(item, Component.literal("§cInvalid item"));
                } else {
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.literal("§7Seller: §f" + listing.sellerName()));
                    lore.add(Component.literal("§6Price: §f" + NumismaticsCompat.formatPrice(listing.price())));
                    lore.add(Component.literal("§7Server: §f" + listing.serverId()));
                    net.minecraft.nbt.CompoundTag ahTag = item.getOrDefault(DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                    ahTag.putLong("arcadia_ah_expires", listing.expiresAt());
                    item.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(ahTag));
                    if (ahMyListings) {
                        lore.add(Component.literal("§c[Click to cancel]").withStyle(ChatFormatting.RED));
                    } else {
                        lore.add(Component.literal("§a[Click to buy]").withStyle(ChatFormatting.GREEN));
                    }
                    net.minecraft.world.item.component.ItemLore existingLore = item.get(DataComponents.LORE);
                    if (existingLore != null) {
                        List<Component> merged = new ArrayList<>(existingLore.lines());
                        merged.addAll(lore);
                        lore = merged;
                    }
                    item.set(DataComponents.LORE, new ItemLore(lore));
                }
                if (listing.sellerUuid().equals(player.getUUID()))
                    item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                container.setItem(slot, item);
            } else {
                ItemStack empty = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
                setName(empty, Component.literal("§8No listing"));
                container.setItem(slot, empty);
            }
        }

        buildBottomBar(container, ahPage, maxPage);

        String curSearch = AuctionManager.getSearch(player.getUUID());
        ItemStack search = new ItemStack(Items.SPYGLASS);
        setName(search, Component.literal("§bSearch").withStyle(ChatFormatting.AQUA));
        setLore(search, List.of(
                Component.literal(curSearch.isEmpty() ? "§7Click to search..." : "§fQuery: §e" + curSearch),
                Component.literal("§7Click to open search").withStyle(ChatFormatting.GRAY)));
        container.setItem(46, search);

        String catDisplay = ahCategory.isEmpty() ? "All" : (ahCategory.equals("pet") ? "Pets" : "Misc");
        ItemStack filter = new ItemStack(Items.HOPPER);
        setName(filter, Component.literal("§dFilter: §f" + catDisplay));
        setLore(filter, List.of(Component.literal("§7Click to cycle: All → Pets → Misc").withStyle(ChatFormatting.GRAY)));
        container.setItem(47, filter);

        ItemStack lb = new ItemStack(Items.NETHER_STAR);
        setName(lb, Component.literal("⭐ Top Business").withStyle(ChatFormatting.GOLD));
        setLore(lb, List.of(Component.literal("View top sellers by unique clients").withStyle(ChatFormatting.GRAY)));
        container.setItem(48, lb);

        ItemStack pageInfo = new ItemStack(Items.PAPER);
        setName(pageInfo, Component.literal("§fPage " + (ahPage + 1) + " / " + (maxPage + 1)).withStyle(ChatFormatting.WHITE));
        setLore(pageInfo, List.of(Component.literal(total + " listing(s)").withStyle(ChatFormatting.GRAY)));
        container.setItem(49, pageInfo);

        ItemStack myBtn = new ItemStack(ahMyListings ? Items.LIME_DYE : Items.GRAY_DYE);
        setName(myBtn, Component.literal(ahMyListings ? "§a◉ My Listings" : "§7◎ My Listings"));
        setLore(myBtn, List.of(Component.literal(ahMyListings ? "§7Click to browse all" : "§7Click to see yours").withStyle(ChatFormatting.GRAY)));
        container.setItem(51, myBtn);
    }

    @Override
    public boolean handleClick(int slotId, int button, ServerPlayer sp, Runnable refreshTab) {
        if (slotId == 45) {
            if (ahPage > 0) { ahPage--; refreshTab.run(); }
            return true;
        }
        if (slotId == 53) {
            List<AuctionListing> filtered = getFiltered(sp);
            if ((ahPage + 1) * 36 < filtered.size()) { ahPage++; refreshTab.run(); }
            return true;
        }
        if (slotId == 46) {
            PacketDistributor.sendToPlayer(sp, new S2COpenAhSearch(AuctionManager.getSearch(sp.getUUID())));
            return true;
        }
        if (slotId == 47) {
            ahCategory = switch (ahCategory) {
                case "" -> "pet";
                case "pet" -> "misc";
                default -> "";
            };
            ahPage = 0;
            refreshTab.run();
            return true;
        }
        if (slotId == 48) {
            AhLeaderboardMenu.openFor(sp);
            return true;
        }
        if (slotId == 51) {
            ahMyListings = !ahMyListings;
            ahPage = 0;
            refreshTab.run();
            return true;
        }
        if (slotId >= 9 && slotId <= 44) {
            int idx = ahPage * 36 + (slotId - 9);
            List<AuctionListing> filtered = getFiltered(sp);
            if (idx >= filtered.size()) return true;
            AuctionListing listing = filtered.get(idx);
            if (ahMyListings) {
                AuctionManager.cancelListing(sp, listing.listingId(), sp.getServer());
            } else {
                AuctionManager.buyListing(sp, listing.listingId(), sp.getServer());
            }
            refreshTab.run();
            return true;
        }
        return false;
    }

    @Override
    public void onClose(ServerPlayer player) {
        // Search cleared on tab switch (DashboardMenu.switchTab), not here,
        // so it survives the search-screen → dashboard reopen cycle.
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<AuctionListing> getFiltered(ServerPlayer sp) {
        String search = AuctionManager.getSearch(sp.getUUID());
        if (ahMyListings) return AuctionManager.getByPlayer(sp.getUUID());
        return AuctionManager.getFiltered(ahCategory, search);
    }

    private void buildBottomBar(SimpleContainer container, int page, int maxPage) {
        for (int i = 45; i <= 53; i++) {
            ItemStack g = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            setName(g, Component.literal(" "));
            container.setItem(i, g);
        }
        container.setItem(45, arrowItem("◀ Previous"));
        ItemStack pi = new ItemStack(Items.PAPER);
        setName(pi, Component.literal("§fPage " + (page + 1) + " / " + (maxPage + 1)));
        container.setItem(49, pi);
        container.setItem(53, arrowItem("Next ▶"));
    }

    private ItemStack arrowItem(String label) {
        ItemStack s = new ItemStack(Items.ARROW);
        setName(s, Component.literal(label).withStyle(ChatFormatting.AQUA));
        return s;
    }

    private static void setName(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    private static void setLore(ItemStack stack, List<Component> lore) {
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }
}
