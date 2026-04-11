package com.arcadia.lib.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Common player utility methods shared across all Arcadia mods.
 * Eliminates the repeated giveOrDrop pattern and player lookup boilerplate.
 */
public final class PlayerUtils {

    private PlayerUtils() {}

    /**
     * Gives an item to a player. If inventory is full, drops it at their feet.
     * This pattern appears 8+ times across the codebase.
     */
    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Gives multiple items, dropping any that don't fit. */
    public static void giveOrDrop(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) giveOrDrop(player, stack);
    }

    /** Finds an online player by UUID via PlayerManager. Returns null if offline. */
    public static ServerPlayer findOnline(UUID uuid) {
        return com.arcadia.lib.player.PlayerManager.getPlayer(uuid);
    }

    /** Finds an online player by name. Returns null if offline. */
    public static ServerPlayer findOnline(String name) {
        for (ServerPlayer sp : com.arcadia.lib.player.PlayerManager.getOnlinePlayers()) {
            if (sp.getName().getString().equalsIgnoreCase(name)) return sp;
        }
        return null;
    }

    /** Returns true if the player's inventory has at least one empty slot. */
    public static boolean hasInventorySpace(ServerPlayer player) {
        return player.getInventory().getFreeSlot() != -1;
    }

    /** Counts how many of a specific item the player has in their inventory. */
    public static int countItem(ServerPlayer player, net.minecraft.world.level.ItemLike item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == item.asItem()) count += s.getCount();
        }
        return count;
    }
}
