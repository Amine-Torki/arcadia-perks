package com.arcadia.lib.economy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Vanilla item-based economy backend. Uses a configurable item (default: emerald)
 * as currency. Counts items in the player's inventory for balance, removes for
 * deduction, and gives for addition.
 */
public final class ItemCurrencyBackend implements EconomyBackend {

    private Item cachedItem;

    private Item getCurrencyItem() {
        if (cachedItem == null) {
            ResourceLocation id = ResourceLocation.parse(EconomyConfig.ITEM_ID);
            cachedItem = BuiltInRegistries.ITEM.get(id);
            if (cachedItem == null || cachedItem == Items.AIR) cachedItem = Items.EMERALD;
        }
        return cachedItem;
    }

    /** Resets cached item (call after config reload). */
    public void resetCache() { cachedItem = null; }

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public String getName() { return "Item Currency (" + EconomyConfig.ITEM_ID + ")"; }

    @Override
    public long getBalance(ServerPlayer player) {
        Item currency = getCurrencyItem();
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == currency) count += s.getCount();
        }
        return count;
    }

    @Override
    public boolean deduct(ServerPlayer player, long amount) {
        if (getBalance(player) < amount) return false;
        Item currency = getCurrencyItem();
        int remaining = (int) amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == currency) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
            }
        }
        return remaining <= 0;
    }

    @Override
    public void add(ServerPlayer player, long amount) {
        Item currency = getCurrencyItem();
        int toGive = (int) amount;
        while (toGive > 0) {
            int stack = Math.min(toGive, currency.getDefaultMaxStackSize());
            ItemStack give = new ItemStack(currency, stack);
            if (!player.getInventory().add(give)) player.drop(give, false);
            toGive -= stack;
        }
    }

    @Override
    public String formatPrice(long amount) {
        if (amount <= 0) return "Free";
        return amount + " " + EconomyConfig.DISPLAY_NAME;
    }
}
