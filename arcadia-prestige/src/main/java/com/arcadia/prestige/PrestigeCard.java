package com.arcadia.prestige;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared data for a /prestige hub card.
 * Used by both PrestigeHubScreen (client rendering) and DashboardMenu (server nav bar items).
 *
 * <p>The ALL list always contains all four cards so tab indices stay stable.
 * Cards whose module is absent are marked {@link #available} = false and
 * rendered as grayed-out in the hub.</p>
 */
public record PrestigeCard(
        String emoji,
        String labelKey,
        String sublabelKey,
        int color,
        int tab,
        Item navIcon,
        boolean available
) {
    // Constructor for always-available cards (cosmetics, daily)
    public PrestigeCard(String emoji, String labelKey, String sublabelKey,
                        int color, int tab, Item navIcon) {
        this(emoji, labelKey, sublabelKey, color, tab, navIcon, true);
    }

    /** All four cards. Available flag is computed once at first access. */
    public static final List<PrestigeCard> ALL = buildAll();

    private static List<PrestigeCard> buildAll() {
        boolean hasPets = ModList.get().isLoaded("arcadia_pets");
        boolean hasAh   = ModList.get().isLoaded("arcadia_ah");
        List<PrestigeCard> list = new ArrayList<>();
        list.add(new PrestigeCard("✨", "arcadia_prestige.hub.cosmetics.label", "arcadia_prestige.hub.cosmetics.sub", 0x6BB8D4, 0, Items.NETHER_STAR));
        list.add(new PrestigeCard("♦",  "arcadia_prestige.hub.pets.label",      "arcadia_prestige.hub.pets.sub",      0x4ECCA3, 1, Items.BONE,        hasPets));
        list.add(new PrestigeCard("⭐", "arcadia_prestige.hub.daily.label",      "arcadia_prestige.hub.daily.sub",     0xD4A847, 2, Items.CLOCK));
        list.add(new PrestigeCard("★",  "arcadia_prestige.hub.auction.label",    "arcadia_prestige.hub.auction.sub",   0xB87333, 3, Items.EMERALD,     hasAh));
        return Collections.unmodifiableList(list);
    }
}
