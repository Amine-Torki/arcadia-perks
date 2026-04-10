package com.arcadia.lib.client;

import org.jetbrains.annotations.Nullable;

/**
 * Describes a module card displayed in the Arcadia Hub.
 * Each Arcadia mod registers one or more cards during initialization.
 * The hub screen renders all registered cards and detects availability automatically.
 *
 * @param id             unique identifier (e.g. "cosmetics", "pets", "ah")
 * @param emoji          icon emoji displayed on the card
 * @param labelKey       translation key for the card title
 * @param sublabelKey    translation key for the card subtitle
 * @param color          accent color (RRGGBB without alpha)
 * @param sortOrder      display order in the hub (lower = further left)
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
        boolean available,
        @Nullable String permissionNode
) {
    /** Backward-compatible constructor without permission node. */
    public ArcadiaModCard(String id, String emoji, String labelKey, String sublabelKey,
                          int color, int sortOrder, boolean available) {
        this(id, emoji, labelKey, sublabelKey, color, sortOrder, available, null);
    }
}
