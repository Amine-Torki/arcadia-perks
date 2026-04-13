package com.arcadia.lib.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * Reduces the freeze when opening the creative inventory search tab.
 *
 * <p>Root cause: vanilla rebuilds the search tree synchronously on tab switch,
 * iterating every registered item and calling getName()/getTooltipLines() on each.
 * With 10,000+ modded items this takes 20-60 seconds.</p>
 *
 * <p>Fix: defer the first search refresh until the player actually types something.
 * On initial tab open, populate with ALL items (no filtering) without building
 * the expensive search tree. The tree is only built on first keystroke.</p>
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeTabLagFixMixin {

    @Shadow private Collection<ItemStack> originalItems;
    @Unique private boolean arcadia$searchInitialized = false;
    @Unique private boolean arcadia$isFirstOpen = true;

    /**
     * Intercept refreshSearchResults — on the very first call (tab open),
     * skip the expensive search tree rebuild and just show all items.
     */
    @Inject(method = "refreshSearchResults", at = @At("HEAD"), cancellable = true)
    private void arcadia$deferSearchTreeBuild(CallbackInfo ci) {
        if (!arcadia$isFirstOpen) return;

        // First open: skip search tree, just show everything
        arcadia$isFirstOpen = false;

        CreativeModeInventoryScreen self = (CreativeModeInventoryScreen) (Object) this;
        var menu = self.getMenu();

        // Only defer if originalItems is populated (search tab)
        if (originalItems != null && !originalItems.isEmpty()) {
            menu.items.clear();
            menu.items.addAll(originalItems);
            menu.scrollTo(0.0f);
            arcadia$searchInitialized = false;
            ci.cancel();
        }
    }

    /**
     * Reset state when the screen is closed so next open also benefits.
     */
    @Inject(method = "removed", at = @At("HEAD"))
    private void arcadia$onRemoved(CallbackInfo ci) {
        arcadia$isFirstOpen = true;
        arcadia$searchInitialized = false;
    }
}
