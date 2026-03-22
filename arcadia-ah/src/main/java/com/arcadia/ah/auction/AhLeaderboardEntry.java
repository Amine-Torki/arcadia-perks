package com.arcadia.ah.auction;

/**
 * One row in the AH leaderboard.
 *
 * Ranking is intentionally fraud-resistant:
 *  - Primary key: unique buyers (can't inflate with 1 alt)
 *  - Secondary key: median sale amount per unique buyer (not sum — prevents
 *    a friend buying the same cheap item 1000× to inflate the average)
 *  - Total sales shown for info but NOT used for ranking
 */
public record AhLeaderboardEntry(
        String sellerName,
        int uniqueBuyers,
        int totalSales,
        long medianAmountPerBuyer,  // median of (total spent per buyer) — tiebreaker
        long totalRevenue
) {}
