package com.arcadia.lib.client;

import com.arcadia.lib.ArcadiaLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Locale;

/**
 * Adds @modid search support to the creative inventory search tab.
 * Uses NeoForge ScreenEvent (no mixin) — guaranteed to work in production.
 *
 * <p>When the search query starts with '@', filters the visible items by
 * mod namespace. Example: @create shows all Create mod items.</p>
 */
@EventBusSubscriber(modid = ArcadiaLib.MOD_ID, value = Dist.CLIENT)
public final class CreativeSearchHandler {

    private static String lastQuery = "";

    private CreativeSearchHandler() {}

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;

        // Find the search EditBox
        EditBox searchBox = null;
        for (var child : screen.children()) {
            if (child instanceof EditBox eb) { searchBox = eb; break; }
        }
        if (searchBox == null) return;

        String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);

        // Only act on @modid queries
        if (!query.startsWith("@") || query.length() < 2) {
            if (lastQuery.startsWith("@")) {
                lastQuery = query; // Reset — let vanilla handle
            }
            return;
        }

        // Avoid re-filtering every frame if the query hasn't changed
        if (query.equals(lastQuery)) return;
        lastQuery = query;

        String modFilter = query.substring(1);

        // Filter the menu items by mod namespace
        try {
            var menu = screen.getMenu();
            menu.items.clear();

            for (var entry : BuiltInRegistries.ITEM) {
                var key = BuiltInRegistries.ITEM.getKey(entry);
                if (key.getNamespace().toLowerCase(Locale.ROOT).contains(modFilter)) {
                    menu.items.add(new ItemStack(entry));
                }
            }

            menu.scrollTo(0.0f);
        } catch (Exception ignored) {
            // Silently fail — never crash the game
        }
    }
}
