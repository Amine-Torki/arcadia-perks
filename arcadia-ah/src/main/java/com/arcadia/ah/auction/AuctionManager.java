package com.arcadia.ah.auction;


import com.arcadia.pets.item.PetItem;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Business logic for the Auction House.
 * Maintains a server-side in-memory cache of active listings,
 * refreshed from DB on demand and periodically.
 */
public final class AuctionManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** How long listings stay active (48 hours). */
    public static final long LISTING_DURATION_MS = 48L * 60 * 60 * 1000;

    /** Max items a player may have listed simultaneously. */
    public static final int MAX_LISTINGS_PER_PLAYER = 30;

    /** In-memory cache — refreshed from DB periodically. */
    private static volatile List<AuctionListing> cache = new CopyOnWriteArrayList<>();

    /** Server-side search state per player UUID. */
    private static final Map<UUID, String> playerSearch = new HashMap<>();

    /** Timestamp of last search per player — throttled to 1 query/sec. */
    private static final Map<UUID, Long> lastSearchTime = new HashMap<>();

    private static final long SEARCH_COOLDOWN_MS = 1_000L;

    private AuctionManager() {}

    // -------------------------------------------------------------------------
    // Cache management
    // -------------------------------------------------------------------------

    public static void refreshCache() {
        com.arcadia.lib.data.DatabaseManager.executeAsync(() -> {
            List<AuctionListing> fresh = AuctionDatabase.fetchAllActive();
            cache = new CopyOnWriteArrayList<>(fresh);
        });
    }

    public static List<AuctionListing> getCache() { return cache; }

    // -------------------------------------------------------------------------
    // Filtering / search helpers
    // -------------------------------------------------------------------------

    public static void setSearch(UUID playerUuid, String query) {
        long now = System.currentTimeMillis();
        Long last = lastSearchTime.get(playerUuid);
        if (last != null && now - last < SEARCH_COOLDOWN_MS) return; // throttle
        lastSearchTime.put(playerUuid, now);
        playerSearch.put(playerUuid, query == null ? "" : query.toLowerCase().trim());
    }

    public static String getSearch(UUID playerUuid) {
        return playerSearch.getOrDefault(playerUuid, "");
    }

    public static void clearSearch(UUID playerUuid) {
        playerSearch.remove(playerUuid);
        lastSearchTime.remove(playerUuid);
    }

    /**
     * Returns filtered + searched listings for display.
     * @param category null = all categories
     * @param search   empty = no filter
     */
    public static List<AuctionListing> getFiltered(String category, String search) {
        return cache.stream()
                .filter(l -> category == null || category.isEmpty() || category.equals(l.category()))
                .filter(l -> search == null || search.isEmpty()
                        || l.itemDisplayName().toLowerCase().contains(search)
                        || l.sellerName().toLowerCase().contains(search))
                .toList();
    }

    public static List<AuctionListing> getByPlayer(UUID playerUuid) {
        return cache.stream().filter(l -> l.sellerUuid().equals(playerUuid)).toList();
    }

    // -------------------------------------------------------------------------
    // List an item
    // -------------------------------------------------------------------------

    public static boolean listItem(ServerPlayer seller, ItemStack stack, long price, MinecraftServer server) {
        if (stack.isEmpty()) return false;
        if (price <= 0) {
            seller.sendSystemMessage(Component.literal("§cPrice must be greater than 0."));
            return false;
        }

        List<AuctionListing> myListings = getByPlayer(seller.getUUID());
        if (myListings.size() >= MAX_LISTINGS_PER_PLAYER) {
            seller.sendSystemMessage(Component.literal(
                    "§cYou already have " + MAX_LISTINGS_PER_PLAYER + " active listings."));
            return false;
        }

        net.minecraft.core.HolderLookup.Provider reg = server.registryAccess();
        String nbt = AuctionItemSerializer.toBase64(stack, reg);
        if (nbt.isEmpty()) return false;

        String category = (stack.getItem() instanceof PetItem) ? "pet" : "misc";
        long now = System.currentTimeMillis();

        AuctionListing listing = new AuctionListing(
                UUID.randomUUID(),
                System.getProperty("arcadia.server_id", "server1"),
                seller.getUUID(),
                seller.getGameProfile().getName(),
                nbt,
                stack.getHoverName().getString(),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                category,
                price,
                now,
                now + LISTING_DURATION_MS
        );

        com.arcadia.lib.data.DatabaseManager.executeAsync(() -> {
            AuctionDatabase.insertListing(listing);
            refreshCache();
        });

        cache.add(listing); // optimistic local add
        int qty = stack.getCount();
        String itemName = stack.getHoverName().getString();
        if (qty > 1) {
            seller.sendSystemMessage(Component.literal(
                    "§a[AH] Listed §f" + qty + "×" + itemName
                    + " §afor §f" + NumismaticsCompat.formatPrice(price) + " §7total §a(§f"
                    + NumismaticsCompat.formatPrice(price / qty) + "§7/unit§a)."));
        } else {
            seller.sendSystemMessage(Component.literal(
                    "§a[AH] Listed §f" + itemName
                    + " §afor §f" + NumismaticsCompat.formatPrice(price) + "§a."));
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Buy a listing
    // -------------------------------------------------------------------------

    public static void buyListing(ServerPlayer buyer, UUID listingId, MinecraftServer server) {
        Optional<AuctionListing> opt = cache.stream()
                .filter(l -> l.listingId().equals(listingId)).findFirst();
        if (opt.isEmpty()) {
            buyer.sendSystemMessage(Component.literal("§cListing no longer available."));
            return;
        }
        AuctionListing listing = opt.get();

        if (listing.sellerUuid().equals(buyer.getUUID())) {
            buyer.sendSystemMessage(Component.literal("§cYou cannot buy your own listing."));
            return;
        }

        if (!NumismaticsCompat.deductBalance(buyer, listing.price())) {
            buyer.sendSystemMessage(Component.literal(
                    "§cNot enough funds. Need §f" + NumismaticsCompat.formatPrice(listing.price()) + "§c."));
            return;
        }

        // Remove from cache immediately to prevent double-buys
        cache.removeIf(l -> l.listingId().equals(listingId));

        net.minecraft.core.HolderLookup.Provider reg = server.registryAccess();
        ItemStack item = AuctionItemSerializer.fromBase64(listing.itemNbt(), reg);

        // Give item to buyer
        if (!item.isEmpty()) {
            if (!buyer.getInventory().add(item)) {
                buyer.drop(item, false);
            }
        }

        // Pay seller via mailbox (works cross-server)
        MailboxEntry payment = new MailboxEntry(
                UUID.randomUUID(),
                listing.sellerUuid(),
                "coins",
                null,
                listing.price(),
                "Sale of " + listing.itemDisplayName() + " to " + buyer.getGameProfile().getName(),
                System.currentTimeMillis()
        );

        com.arcadia.lib.data.DatabaseManager.executeAsync(() -> {
            AuctionDatabase.deleteListing(listingId);
            AuctionDatabase.insertMailbox(payment);
            AuctionDatabase.logSale(listing.sellerUuid(), listing.sellerName(),
                    buyer.getUUID(), listing.price());
        });

        // If seller is online on THIS server, pay them directly
        ServerPlayer sellerOnline = server.getPlayerList().getPlayer(listing.sellerUuid());
        if (sellerOnline != null) {
            NumismaticsCompat.addBalance(sellerOnline, listing.price());
            AuctionDatabase.deleteMailboxEntry(payment.entryId());
            sellerOnline.sendSystemMessage(Component.literal(
                    "§6[AH] §f" + buyer.getGameProfile().getName()
                    + " §6bought your §f" + listing.itemDisplayName()
                    + " §6for §f" + NumismaticsCompat.formatPrice(listing.price()) + "§6."));
        }

        buyer.sendSystemMessage(Component.literal(
                "§a[AH] Bought §f" + listing.itemDisplayName()
                + " §afor §f" + NumismaticsCompat.formatPrice(listing.price()) + "§a."));
    }

    // -------------------------------------------------------------------------
    // Cancel a listing (seller only)
    // -------------------------------------------------------------------------

    public static void cancelListing(ServerPlayer seller, UUID listingId, MinecraftServer server) {
        Optional<AuctionListing> opt = cache.stream()
                .filter(l -> l.listingId().equals(listingId)).findFirst();
        if (opt.isEmpty()) {
            seller.sendSystemMessage(Component.literal("§cListing not found."));
            return;
        }
        AuctionListing listing = opt.get();
        if (!listing.sellerUuid().equals(seller.getUUID())) {
            seller.sendSystemMessage(Component.literal("§cThis is not your listing."));
            return;
        }

        cache.removeIf(l -> l.listingId().equals(listingId));

        // Return item to seller
        net.minecraft.core.HolderLookup.Provider reg = server.registryAccess();
        ItemStack item = AuctionItemSerializer.fromBase64(listing.itemNbt(), reg);
        if (!item.isEmpty()) {
            if (!seller.getInventory().add(item)) seller.drop(item, false);
        }

        com.arcadia.lib.data.DatabaseManager.executeAsync(() ->
                AuctionDatabase.deleteListing(listingId));

        seller.sendSystemMessage(Component.literal(
                "§e[AH] Cancelled listing for §f" + listing.itemDisplayName() + "§e. Item returned."));
    }

    // -------------------------------------------------------------------------
    // Expiry sweep (called by AuctionEventHandler tick)
    // -------------------------------------------------------------------------

    public static void processExpired(MinecraftServer server) {
        com.arcadia.lib.data.DatabaseManager.executeAsync(() -> {
            List<AuctionListing> expired = AuctionDatabase.fetchExpired();
            for (AuctionListing listing : expired) {
                // Return item to seller via mailbox
                MailboxEntry ret = new MailboxEntry(
                        UUID.randomUUID(),
                        listing.sellerUuid(),
                        "item",
                        listing.itemNbt(),
                        0,
                        "Expired listing: " + listing.itemDisplayName(),
                        System.currentTimeMillis()
                );
                AuctionDatabase.insertMailbox(ret);
                AuctionDatabase.deleteListing(listing.listingId());

                // If seller online, deliver immediately
                ServerPlayer online = server.getPlayerList().getPlayer(listing.sellerUuid());
                if (online != null) drainMailbox(online, server);
            }
            if (!expired.isEmpty()) refreshCache();
        });
    }

    // -------------------------------------------------------------------------
    // Mailbox drain (called on login and after online purchase)
    // -------------------------------------------------------------------------

    public static void drainMailbox(ServerPlayer player, MinecraftServer server) {
        com.arcadia.lib.data.DatabaseManager.executeAsync(() -> {
            List<MailboxEntry> entries = AuctionDatabase.fetchMailbox(player.getUUID());
            if (entries.isEmpty()) return;

            net.minecraft.core.HolderLookup.Provider reg = server.registryAccess();
            for (MailboxEntry entry : entries) {
                if ("coins".equals(entry.type())) {
                    NumismaticsCompat.addBalance(player, entry.coins());
                    String reason = entry.reason() != null ? entry.reason() : "Auction sale";
                    player.sendSystemMessage(Component.literal(
                            "§6[AH Mailbox] §f" + NumismaticsCompat.formatPrice(entry.coins())
                            + " §6received: §7" + reason));
                } else if ("item".equals(entry.type()) && entry.itemNbt() != null) {
                    ItemStack item = AuctionItemSerializer.fromBase64(entry.itemNbt(), reg);
                    if (!item.isEmpty()) {
                        if (!player.getInventory().add(item)) player.drop(item, false);
                        String reason = entry.reason() != null ? entry.reason() : "Auction return";
                        player.sendSystemMessage(Component.literal(
                                "§6[AH Mailbox] Item returned: §f"
                                + item.getHoverName().getString() + " §7(" + reason + ")"));
                    }
                }
                AuctionDatabase.deleteMailboxEntry(entry.entryId());
            }
        });
    }
}
