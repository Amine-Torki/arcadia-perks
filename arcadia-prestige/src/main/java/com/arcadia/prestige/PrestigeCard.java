package com.arcadia.prestige;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Shared data for a /prestige hub card.
 * Used by both PrestigeHubScreen (client rendering) and DashboardMenu (server nav bar items).
 */
public record PrestigeCard(
        String emoji,
        String labelKey,
        String sublabelKey,
        int color,
        int tab,
        Item navIcon
) {
    public static final List<PrestigeCard> ALL = List.of(
            new PrestigeCard("✨", "arcadia_prestige.hub.cosmetics.label", "arcadia_prestige.hub.cosmetics.sub", 0x5EAAFF, 0, Items.NETHER_STAR),
            new PrestigeCard("♦",  "arcadia_prestige.hub.pets.label",      "arcadia_prestige.hub.pets.sub",      0x44DD88, 1, Items.BONE),
            new PrestigeCard("⭐", "arcadia_prestige.hub.daily.label",     "arcadia_prestige.hub.daily.sub",     0xFFCC33, 2, Items.CLOCK),
            new PrestigeCard("★",  "arcadia_prestige.hub.auction.label",   "arcadia_prestige.hub.auction.sub",   0xFFAA00, 3, Items.EMERALD)
    );
}
