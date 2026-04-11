package com.arcadia.lib.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for constructing styled ItemStacks (menus, GUIs, rewards).
 *
 * <pre>
 * ItemStack icon = ItemBuilder.of(Items.DIAMOND_SWORD)
 *     .name(Component.literal("Excalibur").withStyle(ChatFormatting.GOLD))
 *     .lore(Component.literal("A legendary blade"))
 *     .enchanted()
 *     .build();
 * </pre>
 */
public final class ItemBuilder {

    private final ItemStack stack;
    private final List<Component> lore = new ArrayList<>();

    private ItemBuilder(ItemStack stack) {
        this.stack = stack;
    }

    public static ItemBuilder of(ItemLike item) {
        return new ItemBuilder(new ItemStack(item));
    }

    public static ItemBuilder of(ItemLike item, int count) {
        return new ItemBuilder(new ItemStack(item, count));
    }

    public static ItemBuilder of(ItemStack stack) {
        return new ItemBuilder(stack.copy());
    }

    /** Sets the display name. */
    public ItemBuilder name(Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
        return this;
    }

    /** Sets the display name from a string. */
    public ItemBuilder name(String name) {
        return name(Component.literal(name));
    }

    /** Sets the lore (replaces any existing). */
    public ItemBuilder lore(Component... lines) {
        lore.clear();
        lore.addAll(List.of(lines));
        return this;
    }

    /** Sets the lore from a list. */
    public ItemBuilder lore(List<Component> lines) {
        lore.clear();
        lore.addAll(lines);
        return this;
    }

    /** Appends a single lore line. */
    public ItemBuilder addLore(Component line) {
        lore.add(line);
        return this;
    }

    /** Appends a lore line from string. */
    public ItemBuilder addLore(String line) {
        return addLore(Component.literal(line));
    }

    /** Adds enchantment glint (visual only). */
    public ItemBuilder enchanted() {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return this;
    }

    /** Sets the item count. */
    public ItemBuilder count(int count) {
        stack.setCount(count);
        return this;
    }

    /** Builds the final ItemStack. */
    public ItemStack build() {
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(List.copyOf(lore)));
        }
        return stack;
    }
}
