package com.arcadia.lib.dashboard;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Pluggable handler for a single Arcadia Dashboard tab.
 * Implemented by arcadia-pets (tab 1) and arcadia-ah (tab 3).
 * Defined in arcadia-lib so all modules can reference it without circular deps.
 */
public interface DashboardTabHandler {

    /** Populate slots 9–53 for this tab. broadcastChanges is called by DashboardMenu after this returns. */
    void buildTab(SimpleContainer container, ServerPlayer player);

    /** Handle a click in slots 9–53. Return true if consumed. */
    boolean handleClick(int slotId, int button, ServerPlayer player, Runnable refreshTab);

    /** Item to show in nav-bar slot 4 (e.g. active pet). Return EMPTY to show the default filler. */
    default ItemStack getNavBarItem(ServerPlayer player) { return ItemStack.EMPTY; }

    /** Handle a click on nav-bar slot 4. Return true if consumed. */
    default boolean handleNavBarClick(ServerPlayer player, Runnable refreshTab) { return false; }

    /** Handle shift-click from the player inventory while this tab is active. Return true if consumed. */
    default boolean handleInventoryShiftClick(Slot slot, ServerPlayer player, Runnable refreshTab) { return false; }

    /** Called when the dashboard menu is closed. Use to reset transient state (e.g. search queries). */
    default void onClose(ServerPlayer player) {}
}
