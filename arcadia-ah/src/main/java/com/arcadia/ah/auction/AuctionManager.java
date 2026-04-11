package com.arcadia.ah.auction;


import com.arcadia.ah.config.AhConfig;
// PetItem check via item registry name to avoid depending on arcadia-pets
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Business logic for the Auction House.
 * Maintains a server-side in-memory cache of active listings,
 * refreshed from DB on demand and periodically.
 */
public final class AuctionManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** How long listings stay active — read from AhConfig (default 48 hours). */
    public static long listingDurationMs()     { return AhConfig.LISTING_DURATION_MS; }
    /** Max items a player may have listed simultaneously — read from AhConfig (default 30). */
    public static int  maxListingsPerPlayer()  { return AhConfig.MAX_LISTINGS_PER_PLAYER_V; }

    /** In-memory cache — refreshed from DB periodically. */
    private static volatile List<AuctionListing> cache = new CopyOnWriteArrayList<>();

    /** Listings currently being processed (bought/cancelled) — excluded from cache refresh. */
    private static final Set<UUID> pendingSales = ConcurrentHashMap.newKeySet();

    /** Server-side search state per player UUID. */
    private static final Map<UUID, String> playerSearch = new ConcurrentHashMap<>();

    /** Timestamp of last search per player — throttled to 1 query/sec. */
    private static final Map<UUID, Long> lastSearchTime = new ConcurrentHashMap<>();

    private static final long SEARCH_COOLDOWN_MS = 1_000L;

    private AuctionManager() {}

    // -------------------------------------------------------------------------
    // Cache management
    // -------------------------------------------------------------------------

    public static void refreshCache() {
        com.arcadia.lib.data.DatabaseManager.executeAsync(() -> {
            List<AuctionListing> fresh = AuctionDatabase.fetchAllActive();
            // Filter out listings currently being processed to prevent race condition double-buy
            fresh.removeIf(l -> pendingSales.contains(l.listingId()));
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
            seller.sendSystemMessage(com.arcadia.lib.ArcadiaMessages.error("Price must be greater than 0."));
            return false;
        }

        List<AuctionListing> myListings = getByPlayer(seller.getUUID());
        if (myListings.size() >= maxListingsPerPlayer()) {
            seller.sendSystemMessage(Component.literal(
                    "§cYou already have " + maxListingsPerPlayer() + " active listings."));
            return false;
        }

        net.minecraft.core.HolderLookup.Provider reg = server.registryAccess();
        String nbt = AuctionItemSerializer.toBase64(stack, reg);
        if (nbt.isEmpty()) return false;

        String category = stack.getItem().getClass().getSimpleName().equals("PetItem") ? "pet" : "misc";
        long now = System.currentTimeMillis();

        AuctionListing listing = new AuctionListing(
                UUID.randomUUID(),
                com.arcadia.lib.ServerContext.SERVER_ID,
                seller.getUUID(),
                seller.getGameProfile().getName(),
                nbt,
                stack.getHoverName().getString(),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                category,
                price,
                now,
                now + listingDurationMs()
        );

        com.arcadia.lib.data.DatabaseManager.executeAsync(() -> {
            AuctionDatabase.insertListing(listing);
            refreshCache();
        });

        cache.add(listing); // optimistic local add
        int qty = stack.getCount();
        String itemName = stack.getHoverName().getString();
        if (qty > 1) {
            seller.sendSystemMessage(com.arcadia.lib.ArcadiaMessages.success(
                    "Listed " + qty + "×" + itemName
                    + " for " + NumismaticsCompat.formatPrice(price) + " total ("
                    + NumismaticsCompat.formatPrice(price / qty) + "/unit)."));
        } else {
            seller.sendSystemMessage(com.arcadia.lib.ArcadiaMessages.success(
                    "Listed " + itemName + " for " + NumismaticsCompat.formatPrice(price) + "."));
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
            buyer.sendSystemMessage(com.arcadia.lib.ArcadiaMessages.error("Listing no longer available."));
            return;
        }
        AuctionListing listing = opt.get();

        if (listing.sellerUuid().equals(buyer.getUUID())) {
            buyer.sendSystemMessage(com.arcadia.lib.ArcadiaMessages.error("You cannot buy your own listing."));
            return;
        }

        if (!NumismaticsCompat.deductBalance(buyer, listing.price())) {
            buyer.sendSystemMessage(com.arcadia.lib.ArcadiaMessages.error(
                    "Not enough funds. Need " + NumismaticsCompat.formatPrice(listing.price()) + "."));
            return;
        }

        // Mark as pending sale to prevent race condition with cache refresh
        pendingSales.add(listingId);
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
            pendingSales.remove(listingId); // Safe to re-appear in cache now (deleted from DB)
        });

        // If seller is online on THIS server, pay them directly
        ServerPlayer sellerOnline = server.getPlayerList().getPlayer(listing.sellerUuid());
        if (sellerOnline != null) {
            NumismaticsCompat.addBalance(sellerOnline, listing.price());
            AuctionDatabase.deleteMailboxEntry(payment.entryId());
            sellerOnline.sendSystemMessage(com.arcadia.lib.ArcadiaMessages.success(
                    buyer.getGameProfile().getName() + " bought your "
                    + listing.itemDisplayName() + " for "
                    + NumismaticsCompat.formatPrice(listing.price()) + "."));
        }

        buyer.sendSystemMessage(com.arcadia.lib.ArcadiaMessages.success(
                "Bought " + listing.itemDisplayName()
                + " for " + NumismaticsCompat.formatPrice(listing.price()) + "."));
    }

    // -------------------------------------------------------------------------
    // Cancel a listing (seller only)
    // -------------------------------------------------------------------------

    public static void cancelListing(ServerPlayer seller, UUID listingId, MinecraftServer server) {
        Optional<AuctionListing> opt = cache.stream()
                .filter(l -> l.listingId().equals(listingId)).findFirst();
        if (opt.isEmpty()) {
            seller.sendSystemMessage(com.arcadia.lib.ArcadiaMessages.error("Listing not found."));
            return;
        }
        AuctionListing listing = opt.get();
        if (!listing.sellerUuid().equals(seller.getUUID())) {
            seller.sendSystemMessage(com.arcadia.lib.ArcadiaMessages.error("This is not your listing."));
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

        seller.sendSystemMessage(com.arcadia.lib.ArcadiaMessages.warning(
                "Cancelled listing for " + listing.itemDisplayName() + ". Item returned."));
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

            // Schedule inventory mutations back on the main server thread
            server.execute(() -> {
                net.minecraft.core.HolderLookup.Provider reg = server.registryAccess();
                for (MailboxEntry entry : entries) {
                    if ("coins".equals(entry.type())) {
                        NumismaticsCompat.addBalance(player, entry.coins());
                        String reason = entry.reason() != null ? entry.reason() : "Auction sale";
                        player.sendSystemMessage(Component.literal(
                                "§6⚙ Arcadia §8▸ §a" + NumismaticsCompat.formatPrice(entry.coins())
                                + " §6received: §7" + reason));
                    } else if ("item".equals(entry.type()) && entry.itemNbt() != null) {
                        ItemStack item = AuctionItemSerializer.fromBase64(entry.itemNbt(), reg);
                        if (!item.isEmpty()) {
                            if (!player.getInventory().add(item)) player.drop(item, false);
                            String reason = entry.reason() != null ? entry.reason() : "Auction return";
                            player.sendSystemMessage(Component.literal(
                                    "§6⚙ Arcadia §8▸ §aItem returned: §f"
                                    + item.getHoverName().getString() + " §7(" + reason + ")"));
                        }
                    }
                    // Delete processed entries asynchronously
                    com.arcadia.lib.data.DatabaseManager.executeAsync(() ->
                            AuctionDatabase.deleteMailboxEntry(entry.entryId()));
                }
            });
        });
    }
}
