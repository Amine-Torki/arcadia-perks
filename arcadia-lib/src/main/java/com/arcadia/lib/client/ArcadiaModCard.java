package com.arcadia.lib.client;

import org.jetbrains.annotations.Nullable;

/**
 * Describes a module card displayed in the Arcadia Hub.
 * Each Arcadia mod registers one or more cards during initialization.
 *
 * <p>Cards support multi-row layout: set {@code row} to place cards on
 * different lines (0 = top, 1 = second row, etc.). Within each row,
 * cards are sorted by {@code sortOrder} (lower = further left).</p>
 *
 * @param id             unique identifier (e.g. "cosmetics", "pets", "ah")
 * @param emoji          icon emoji displayed on the card
 * @param labelKey       translation key for the card title
 * @param sublabelKey    translation key for the card subtitle
 * @param color          accent color (RRGGBB without alpha)
 * @param sortOrder      position within its row (lower = further left)
 * @param row            which row to display in (0 = top, 1 = second, etc.)
 * @param available      whether the module is installed/loaded
 * @param permissionNode optional LuckPerms node required to see this card (null = always visible)
 */
public record ArcadiaModCard(
        String id,
        String emoji,
        String labelKey,
        String sublabelKey,
        int color,
        int sortOrder,
        int row,
        boolean available,
        @Nullable String permissionNode
) {
    /** Row 0, no permission (backward compatible). */
    public ArcadiaModCard(String id, String emoji, String labelKey, String sublabelKey,
                          int color, int sortOrder, boolean available) {
        this(id, emoji, labelKey, sublabelKey, color, sortOrder, 0, available, null);
    }

    /** Row 0 with permission. */
    public ArcadiaModCard(String id, String emoji, String labelKey, String sublabelKey,
                          int color, int sortOrder, boolean available, @Nullable String permissionNode) {
        this(id, emoji, labelKey, sublabelKey, color, sortOrder, 0, available, permissionNode);
    }

    /** Explicit row, no permission. */
    public ArcadiaModCard(String id, String emoji, String labelKey, String sublabelKey,
                          int color, int sortOrder, int row, boolean available) {
        this(id, emoji, labelKey, sublabelKey, color, sortOrder, row, available, null);
    }
}
