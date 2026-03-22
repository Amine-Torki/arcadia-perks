package com.arcadia.ah.auction;

import java.util.UUID;

/**
 * A pending delivery for a player (item return or coin payment).
 * Created when a buyer purchases on another server, or when a listing expires.
 */
public record MailboxEntry(
        UUID entryId,
        UUID recipientUuid,
        /** "item" or "coins" */
        String type,
        /** Non-null when type == "item". Base64 NBT blob. */
        String itemNbt,
        /** Non-zero when type == "coins". */
        long coins,
        /** Optional human-readable reason shown on delivery. */
        String reason,
        long createdAt
) {}
