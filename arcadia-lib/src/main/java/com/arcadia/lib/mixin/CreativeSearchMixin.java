package com.arcadia.lib.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Locale;

/**
 * Adds @modid search support to the creative inventory search tab.
 * When the search query starts with '@', filters items by mod namespace
 * instead of searching by name. Example: @arcadia_pets shows all pet items.
 *
 * Also adds '#tag' search by item tag (future extension).
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeSearchMixin {

    @Shadow private Collection<ItemStack> originalItems;

    @Inject(method = "refreshSearchResults", at = @At("HEAD"), cancellable = true)
    private void arcadia$onRefreshSearch(CallbackInfo ci) {
        CreativeModeInventoryScreen self = (CreativeModeInventoryScreen) (Object) this;

        // Access the search box text via reflection-free approach
        String query;
        try {
            var searchBox = self.children().stream()
                    .filter(w -> w instanceof net.minecraft.client.gui.components.EditBox)
                    .map(w -> (net.minecraft.client.gui.components.EditBox) w)
                    .findFirst().orElse(null);
            if (searchBox == null) return;
            query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        } catch (Exception e) { return; }

        if (query.isEmpty() || !query.startsWith("@")) return;

        // @modid search: filter by item registry namespace
        String modId = query.substring(1);
        if (modId.isEmpty()) return;

        var menu = self.getMenu();
        menu.items.clear();

        String finalModId = modId;
        for (ItemStack stack : originalItems) {
            var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key.getNamespace().toLowerCase(Locale.ROOT).contains(finalModId)) {
                menu.items.add(stack);
            }
        }

        menu.scrollTo(0.0f);
        ci.cancel();
    }
}
