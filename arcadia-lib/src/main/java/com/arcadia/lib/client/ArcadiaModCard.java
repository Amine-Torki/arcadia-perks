package com.arcadia.lib.client;

import org.jetbrains.annotations.Nullable;

/**
 * Describes a module card displayed in the Arcadia Hub.
 *
 * @param id             unique identifier (e.g. "cosmetics", "pets", "ah")
 * @param emoji          icon emoji displayed on the card
 * @param labelKey       translation key for the card title
 * @param sublabelKey    translation key for the card subtitle
 * @param color          accent color (RRGGBB without alpha)
 * @param sortOrder      visual position within its row (lower = further left)
 * @param row            which row to display in (0 = top, 1 = second, etc.)
 * @param tabIndex       dashboard tab index to open on click (-1 = use card click handler instead)
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
        int tabIndex,
        boolean available,
        @Nullable String permissionNode
) {
    /** Simple: row 0, tabIndex = sortOrder, no permission. */
    public ArcadiaModCard(String id, String emoji, String labelKey, String sublabelKey,
                          int color, int sortOrder, boolean available) {
        this(id, emoji, labelKey, sublabelKey, color, sortOrder, 0, sortOrder, available, null);
    }

    /** With permission: row 0, tabIndex = sortOrder. */
    public ArcadiaModCard(String id, String emoji, String labelKey, String sublabelKey,
                          int color, int sortOrder, boolean available, @Nullable String permissionNode) {
        this(id, emoji, labelKey, sublabelKey, color, sortOrder, 0, sortOrder, available, permissionNode);
    }

    /** With row: tabIndex = sortOrder, no permission. */
    public ArcadiaModCard(String id, String emoji, String labelKey, String sublabelKey,
                          int color, int sortOrder, int row, boolean available) {
        this(id, emoji, labelKey, sublabelKey, color, sortOrder, row, sortOrder, available, null);
    }

    /** With row + tabIndex, no permission. */
    public ArcadiaModCard(String id, String emoji, String labelKey, String sublabelKey,
                          int color, int sortOrder, int row, int tabIndex, boolean available) {
        this(id, emoji, labelKey, sublabelKey, color, sortOrder, row, tabIndex, available, null);
    }
}
