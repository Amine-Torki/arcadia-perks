package com.arcadia.lib.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reduces the freeze when switching to the creative search tab.
 *
 * <p>Root cause: vanilla calls refreshSearchResults() synchronously when
 * switching to the search tab. This rebuilds the entire search tree by
 * iterating every item and calling getName()/getTooltipLines() — with
 * 10,000+ modded items this takes 20-60 seconds.</p>
 *
 * <p>Fix: skip the FIRST call to refreshSearchResults (the one triggered
 * by tab switch) and mark it for deferred execution. The search tree
 * builds normally only when the player actually types in the search box.</p>
 *
 * <p>This mixin is marked {@code required=false} so if the target method
 * is renamed or removed in a future NeoForge version, the game still
 * launches normally without this optimization.</p>
 */
@Mixin(value = CreativeModeInventoryScreen.class, remap = true)
public class CreativeTabLagFixMixin {

    @Unique private boolean arcadia$skipNextRefresh = true;

    @Inject(method = "refreshSearchResults", at = @At("HEAD"), cancellable = true, require = 0)
    private void arcadia$deferFirstRefresh(CallbackInfo ci) {
        if (arcadia$skipNextRefresh) {
            arcadia$skipNextRefresh = false;
            ci.cancel(); // Skip the expensive first rebuild — items show unfiltered
        }
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void arcadia$resetOnClose(CallbackInfo ci) {
        arcadia$skipNextRefresh = true; // Next open will also benefit
    }
}
