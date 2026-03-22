package com.arcadia.ah.auction;

import java.util.UUID;

/**
 * Immutable snapshot of one auction listing row.
 * itemNbt is the base64-encoded NBT of the ItemStack.
 * price is in Numismatics spurs (1 spur = smallest unit).
 */
public record AuctionListing(
        UUID listingId,
        String serverId,
        UUID sellerUuid,
        String sellerName,
        String itemNbt,        // base64 NBT blob
        String itemDisplayName,
        String itemType,       // e.g. "arcadia_prestige:pet_item"
        String category,       // "pet", "misc"
        long price,
        long listedAt,
        long expiresAt
) {
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
