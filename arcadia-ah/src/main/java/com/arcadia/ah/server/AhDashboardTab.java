package com.arcadia.ah.server;

import com.arcadia.ah.auction.AuctionItemSerializer;
import com.arcadia.ah.auction.AuctionListing;
import com.arcadia.ah.auction.AuctionManager;
// NumismaticsCompat replaced by EconomyService in lib
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
                    lore.add(Component.literal("§7").append(Component.translatable("arcadia_ah.gui.listing.seller")).append(Component.literal("§f" + listing.sellerName())));
                    lore.add(Component.literal("§6").append(Component.translatable("arcadia_ah.gui.listing.price")).append(Component.literal("§f" + com.arcadia.lib.economy.EconomyService.formatPrice(listing.price()))));
                    lore.add(Component.literal("§7").append(Component.translatable("arcadia_ah.gui.listing.server")).append(Component.literal("§f" + listing.serverId())));
                    net.minecraft.nbt.CompoundTag ahTag = item.getOrDefault(DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                    ahTag.putLong("arcadia_ah_expires", listing.expiresAt());
                    item.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(ahTag));
                    if (ahMyListings) {
                        lore.add(Component.translatable("arcadia_ah.gui.listing.click_cancel").withStyle(ChatFormatting.RED));
                    } else {
                        lore.add(Component.translatable("arcadia_ah.gui.listing.click_buy").withStyle(ChatFormatting.GREEN));
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
                setName(empty, Component.translatable("arcadia_ah.gui.listing.no_listing").withStyle(ChatFormatting.DARK_GRAY));
                container.setItem(slot, empty);
            }
        }

        buildBottomBar(container, ahPage, maxPage);

        String curSearch = AuctionManager.getSearch(player.getUUID());
        ItemStack search = new ItemStack(Items.SPYGLASS);
        setName(search, Component.translatable("arcadia_ah.gui.nav.search").withStyle(ChatFormatting.AQUA));
        setLore(search, List.of(
                Component.literal(curSearch.isEmpty() ? "" : "§fQuery: §e" + curSearch)));
        container.setItem(46, search);

        String catDisplay = ahCategory.isEmpty() ? "All" : (ahCategory.equals("pet") ? "Pets" : "Misc");
        ItemStack filter = new ItemStack(Items.HOPPER);
        setName(filter, Component.literal("§d").append(Component.translatable("arcadia_ah.gui.nav.filter")).append(Component.literal(": §f" + catDisplay)));
        container.setItem(47, filter);

        ItemStack lb = new ItemStack(Items.NETHER_STAR);
        setName(lb, Component.translatable("arcadia_ah.gui.leaderboard.title").withStyle(ChatFormatting.GOLD));
        container.setItem(48, lb);

        ItemStack pageInfo = new ItemStack(Items.PAPER);
        setName(pageInfo, Component.literal("§fPage " + (ahPage + 1) + " / " + (maxPage + 1)).withStyle(ChatFormatting.WHITE));
        setLore(pageInfo, List.of(Component.literal(total + " listing(s)").withStyle(ChatFormatting.GRAY)));
        container.setItem(49, pageInfo);

        ItemStack myBtn = new ItemStack(ahMyListings ? Items.LIME_DYE : Items.GRAY_DYE);
        setName(myBtn, Component.literal(ahMyListings ? "§a◉ " : "§7◎ ").append(Component.translatable("arcadia_ah.gui.nav.my_listings")));
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
    public boolean handleInventoryShiftClick(Slot slot, ServerPlayer player, Runnable refreshTab) {
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return false;

        // Send packet to client to open the sell price input screen
        String itemName = stack.getHoverName().getString();
        int slotIndex = slot.getSlotIndex();
        // The slot index in the container is offset — we need the raw inventory slot
        // For player inventory slots in a 54-slot chest menu: slot 54-89 = inv 9-44, 90-98 = hotbar 0-8
        int invSlot = slot.getContainerSlot();
        PacketDistributor.sendToPlayer(player,
                new com.arcadia.ah.network.S2COpenAhSell(itemName, stack.getCount(), invSlot));
        return true;
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
        container.setItem(45, arrowItem(Component.translatable("arcadia_ah.gui.nav.prev").getString()));
        ItemStack pi = new ItemStack(Items.PAPER);
        setName(pi, Component.literal("§fPage " + (page + 1) + " / " + (maxPage + 1)));
        container.setItem(49, pi);
        container.setItem(53, arrowItem(Component.translatable("arcadia_ah.gui.nav.next").getString()));
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
