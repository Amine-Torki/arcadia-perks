package com.arcadia.ah.auction;

import com.arcadia.lib.data.DatabaseManager;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Low-level CRUD for arcadia_prestige_auction_listings and arcadia_prestige_auction_mailbox tables.
 * Falls back to thread-safe in-memory lists when DatabaseManager is in debug mode.
 */
public final class AuctionDatabase {

    private static final Logger LOGGER = LogUtils.getLogger();

    // In-memory fallback for debug mode
    private static final List<AuctionListing> DEBUG_LISTINGS  = new CopyOnWriteArrayList<>();
    private static final List<MailboxEntry>   DEBUG_MAILBOX   = new CopyOnWriteArrayList<>();

    /** Debug-mode sales log: [sellerUuid, buyerUuid, amount] */
    private static final List<long[]> DEBUG_SALES_AMOUNTS = new CopyOnWriteArrayList<>();
    private static final List<String[]> DEBUG_SALES_META  = new CopyOnWriteArrayList<>();

    private AuctionDatabase() {}

    // -------------------------------------------------------------------------
    // Table creation (called from DatabaseManager.createTables)
    // -------------------------------------------------------------------------

    public static void createTables() {
        if (DatabaseManager.isDebugMode()) return;
        try (Connection conn = DatabaseManager.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS arcadia_prestige_auction_listings (
                    listing_id   VARCHAR(36)  PRIMARY KEY,
                    server_id    VARCHAR(32)  NOT NULL,
                    seller_uuid  VARCHAR(36)  NOT NULL,
                    seller_name  VARCHAR(32)  NOT NULL,
                    item_nbt     MEDIUMTEXT   NOT NULL,
                    item_name    VARCHAR(128) NOT NULL DEFAULT '',
                    item_type    VARCHAR(128) NOT NULL DEFAULT '',
                    category     VARCHAR(32)  NOT NULL DEFAULT 'misc',
                    price        BIGINT       NOT NULL DEFAULT 0,
                    listed_at    BIGINT       NOT NULL,
                    expires_at   BIGINT       NOT NULL,
                    INDEX idx_seller (seller_uuid),
                    INDEX idx_expires (expires_at)
                )
                """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS arcadia_prestige_auction_mailbox (
                    entry_id      VARCHAR(36) PRIMARY KEY,
                    recipient_uuid VARCHAR(36) NOT NULL,
                    type          VARCHAR(8)  NOT NULL,
                    item_nbt      MEDIUMTEXT,
                    coins         BIGINT      NOT NULL DEFAULT 0,
                    reason        VARCHAR(256),
                    created_at    BIGINT      NOT NULL,
                    INDEX idx_recipient (recipient_uuid)
                )
                """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS arcadia_prestige_auction_sales_log (
                    sale_id      VARCHAR(36) PRIMARY KEY,
                    seller_uuid  VARCHAR(36) NOT NULL,
                    seller_name  VARCHAR(32) NOT NULL,
                    buyer_uuid   VARCHAR(36) NOT NULL,
                    amount       BIGINT      NOT NULL,
                    sold_at      BIGINT      NOT NULL,
                    INDEX idx_sales_seller (seller_uuid),
                    INDEX idx_sales_buyer  (buyer_uuid)
                )
                """);
            LOGGER.info("[ArcadiaPrestige] Auction tables verified.");
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPrestige] Failed to create auction tables", e);
        }
    }

    // -------------------------------------------------------------------------
    // Listings — write
    // -------------------------------------------------------------------------

    public static void insertListing(AuctionListing listing) {
        if (DatabaseManager.isDebugMode()) { DEBUG_LISTINGS.add(listing); return; }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO arcadia_prestige_auction_listings VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, listing.listingId().toString());
            ps.setString(2, listing.serverId());
            ps.setString(3, listing.sellerUuid().toString());
            ps.setString(4, listing.sellerName());
            ps.setString(5, listing.itemNbt());
            ps.setString(6, listing.itemDisplayName());
            ps.setString(7, listing.itemType());
            ps.setString(8, listing.category());
            ps.setLong(9, listing.price());
            ps.setLong(10, listing.listedAt());
            ps.setLong(11, listing.expiresAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPrestige] insertListing failed", e);
        }
    }

    public static void deleteListing(UUID listingId) {
        if (DatabaseManager.isDebugMode()) {
            DEBUG_LISTINGS.removeIf(l -> l.listingId().equals(listingId));
            return;
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM arcadia_prestige_auction_listings WHERE listing_id = ?")) {
            ps.setString(1, listingId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPrestige] deleteListing failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Listings — read
    // -------------------------------------------------------------------------

    public static List<AuctionListing> fetchAllActive() {
        if (DatabaseManager.isDebugMode()) {
            long now = System.currentTimeMillis();
            return DEBUG_LISTINGS.stream().filter(l -> l.expiresAt() > now).toList();
        }
        List<AuctionListing> result = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM arcadia_prestige_auction_listings WHERE expires_at > ? ORDER BY listed_at DESC")) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPrestige] fetchAllActive failed", e);
        }
        return result;
    }

    public static Optional<AuctionListing> fetchById(UUID listingId) {
        if (DatabaseManager.isDebugMode()) {
            return DEBUG_LISTINGS.stream().filter(l -> l.listingId().equals(listingId)).findFirst();
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM arcadia_prestige_auction_listings WHERE listing_id = ?")) {
            ps.setString(1, listingId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromResultSet(rs));
            }
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPrestige] fetchById failed", e);
        }
        return Optional.empty();
    }

    public static List<AuctionListing> fetchExpired() {
        if (DatabaseManager.isDebugMode()) {
            long now = System.currentTimeMillis();
            return DEBUG_LISTINGS.stream().filter(l -> l.expiresAt() <= now).toList();
        }
        List<AuctionListing> result = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM arcadia_prestige_auction_listings WHERE expires_at <= ?")) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPrestige] fetchExpired failed", e);
        }
        return result;
    }

    private static AuctionListing fromResultSet(ResultSet rs) throws SQLException {
        return new AuctionListing(
                UUID.fromString(rs.getString("listing_id")),
                rs.getString("server_id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                rs.getString("item_nbt"),
                rs.getString("item_name"),
                rs.getString("item_type"),
                rs.getString("category"),
                rs.getLong("price"),
                rs.getLong("listed_at"),
                rs.getLong("expires_at")
        );
    }

    // -------------------------------------------------------------------------
    // Mailbox — write
    // -------------------------------------------------------------------------

    public static void insertMailbox(MailboxEntry entry) {
        if (DatabaseManager.isDebugMode()) { DEBUG_MAILBOX.add(entry); return; }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO arcadia_prestige_auction_mailbox VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, entry.entryId().toString());
            ps.setString(2, entry.recipientUuid().toString());
            ps.setString(3, entry.type());
            ps.setString(4, entry.itemNbt());
            ps.setLong(5, entry.coins());
            ps.setString(6, entry.reason());
            ps.setLong(7, entry.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPrestige] insertMailbox failed", e);
        }
    }

    public static void deleteMailboxEntry(UUID entryId) {
        if (DatabaseManager.isDebugMode()) {
            DEBUG_MAILBOX.removeIf(e -> e.entryId().equals(entryId));
            return;
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM arcadia_prestige_auction_mailbox WHERE entry_id = ?")) {
            ps.setString(1, entryId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPrestige] deleteMailboxEntry failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Mailbox — read
    // -------------------------------------------------------------------------

    public static List<MailboxEntry> fetchMailbox(UUID recipientUuid) {
        if (DatabaseManager.isDebugMode()) {
            return DEBUG_MAILBOX.stream()
                    .filter(e -> e.recipientUuid().equals(recipientUuid)).toList();
        }
        List<MailboxEntry> result = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM arcadia_prestige_auction_mailbox WHERE recipient_uuid = ? ORDER BY created_at ASC")) {
            ps.setString(1, recipientUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new MailboxEntry(
                            UUID.fromString(rs.getString("entry_id")),
                            UUID.fromString(rs.getString("recipient_uuid")),
                            rs.getString("type"),
                            rs.getString("item_nbt"),
                            rs.getLong("coins"),
                            rs.getString("reason"),
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPrestige] fetchMailbox failed", e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Sales log
    // -------------------------------------------------------------------------

    public static void logSale(UUID sellerUuid, String sellerName, UUID buyerUuid, long amount) {
        if (DatabaseManager.isDebugMode()) {
            DEBUG_SALES_META.add(new String[]{sellerUuid.toString(), sellerName, buyerUuid.toString()});
            DEBUG_SALES_AMOUNTS.add(new long[]{amount});
            return;
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO arcadia_prestige_auction_sales_log VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, sellerUuid.toString());
            ps.setString(3, sellerName);
            ps.setString(4, buyerUuid.toString());
            ps.setLong(5, amount);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPrestige] logSale failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Leaderboard
    // -------------------------------------------------------------------------

    /**
     * Returns up to {@code limit} leaderboard entries.
     *
     * Ranking: primary = unique buyers DESC, secondary = median per-buyer spend DESC.
     * This makes it impossible to rank by having one friend buy thousands of cheap items:
     *   - unique buyers stays at 1
     *   - median per-buyer spend reflects the actual typical deal size
     */
    public static List<AhLeaderboardEntry> fetchLeaderboard(int limit) {
        if (DatabaseManager.isDebugMode()) {
            return buildDebugLeaderboard(limit);
        }
        List<AhLeaderboardEntry> result = new ArrayList<>();
        // Step 1: per-buyer totals (how much each buyer spent with each seller)
        // Step 2: aggregate across sellers — count unique buyers, median of per-buyer totals
        // MySQL doesn't have a native MEDIAN, so we pull per-seller/per-buyer totals and compute in Java.
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     SELECT seller_uuid, seller_name, buyer_uuid, SUM(amount) as buyer_total
                     FROM arcadia_prestige_auction_sales_log
                     GROUP BY seller_uuid, seller_name, buyer_uuid
                     ORDER BY seller_uuid, buyer_total
                     """)) {
            try (ResultSet rs = ps.executeQuery()) {
                // Group by seller in Java and compute median + stats
                Map<String, String> names = new LinkedHashMap<>();
                Map<String, List<Long>> perBuyerTotals = new LinkedHashMap<>();
                Map<String, Long> totalRevenues = new HashMap<>();
                Map<String, Integer> totalSalesCounts = new HashMap<>();

                // We also need total sale count — run a second query
                try (PreparedStatement ps2 = conn.prepareStatement(
                        "SELECT seller_uuid, COUNT(*) as cnt, SUM(amount) as rev FROM arcadia_prestige_auction_sales_log GROUP BY seller_uuid");
                     ResultSet rs2 = ps2.executeQuery()) {
                    while (rs2.next()) {
                        String su = rs2.getString("seller_uuid");
                        totalSalesCounts.put(su, rs2.getInt("cnt"));
                        totalRevenues.put(su, rs2.getLong("rev"));
                    }
                }

                while (rs.next()) {
                    String su = rs.getString("seller_uuid");
                    names.put(su, rs.getString("seller_name"));
                    perBuyerTotals.computeIfAbsent(su, k -> new ArrayList<>()).add(rs.getLong("buyer_total"));
                }

                for (Map.Entry<String, List<Long>> e : perBuyerTotals.entrySet()) {
                    String su = e.getKey();
                    List<Long> totals = e.getValue(); // already sorted by query
                    int uniqueBuyers = totals.size();
                    long median = totals.get(totals.size() / 2);
                    result.add(new AhLeaderboardEntry(
                            names.get(su),
                            uniqueBuyers,
                            totalSalesCounts.getOrDefault(su, 0),
                            median,
                            totalRevenues.getOrDefault(su, 0L)
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[ArcadiaPrestige] fetchLeaderboard failed", e);
        }

        result.sort(Comparator
                .comparingInt(AhLeaderboardEntry::uniqueBuyers).reversed()
                .thenComparingLong(AhLeaderboardEntry::medianAmountPerBuyer).reversed());
        return result.subList(0, Math.min(limit, result.size()));
    }

    private static List<AhLeaderboardEntry> buildDebugLeaderboard(int limit) {
        // Build from debug sales log
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Set<String>> buyerSets = new LinkedHashMap<>();
        Map<String, Map<String, Long>> perBuyerTotals = new LinkedHashMap<>();
        Map<String, Long> revenues = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (int i = 0; i < DEBUG_SALES_META.size(); i++) {
            String[] meta = DEBUG_SALES_META.get(i);
            long amount = DEBUG_SALES_AMOUNTS.get(i)[0];
            String su = meta[0], sname = meta[1], bu = meta[2];
            names.put(su, sname);
            buyerSets.computeIfAbsent(su, k -> new HashSet<>()).add(bu);
            perBuyerTotals.computeIfAbsent(su, k -> new HashMap<>())
                    .merge(bu, amount, Long::sum);
            revenues.merge(su, amount, Long::sum);
            counts.merge(su, 1, Integer::sum);
        }

        List<AhLeaderboardEntry> result = new ArrayList<>();
        for (String su : names.keySet()) {
            List<Long> buyerTotals = new ArrayList<>(perBuyerTotals.get(su).values());
            Collections.sort(buyerTotals);
            long median = buyerTotals.get(buyerTotals.size() / 2);
            result.add(new AhLeaderboardEntry(
                    names.get(su),
                    buyerSets.get(su).size(),
                    counts.getOrDefault(su, 0),
                    median,
                    revenues.getOrDefault(su, 0L)
            ));
        }
        result.sort(Comparator
                .comparingInt(AhLeaderboardEntry::uniqueBuyers).reversed()
                .thenComparingLong(AhLeaderboardEntry::medianAmountPerBuyer).reversed());
        return result.subList(0, Math.min(limit, result.size()));
    }
}
