package com.arcadia.lib.client;

import com.arcadia.lib.ArcadiaLib;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds the creative item search index in the BACKGROUND on first world join.
 * The vanilla search tree is rebuilt lazily — when the player first opens the
 * creative inventory, the cached list is ready and the freeze is eliminated.
 *
 * <p>The cache is built asynchronously during the loading screen / first seconds
 * of gameplay, so by the time the player opens creative, it's already done.</p>
 */
@EventBusSubscriber(modid = ArcadiaLib.MOD_ID, value = Dist.CLIENT)
public final class CreativeTabCache {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Pre-built list of ALL item stacks for the search tab. */
    private static final AtomicReference<List<ItemStack>> cachedItems = new AtomicReference<>(null);
    private static final AtomicBoolean building = new AtomicBoolean(false);

    private CreativeTabCache() {}

    /**
     * Starts building the item cache in a background thread.
     * Called on first client tick after joining a world.
     */
    public static void prebuildAsync() {
        if (cachedItems.get() != null || !building.compareAndSet(false, true)) return;

        CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            List<ItemStack> items = new ArrayList<>();
            for (var item : BuiltInRegistries.ITEM) {
                try {
                    items.add(new ItemStack(item));
                } catch (Exception ignored) {}
            }
            cachedItems.set(items);
            long elapsed = System.currentTimeMillis() - start;
            LOGGER.info("[ArcadiaLib] Creative item cache built: {} items in {}ms (background thread)",
                    items.size(), elapsed);
        });
    }

    /** Returns the cached items, or null if not yet built. */
    public static List<ItemStack> getCachedItems() {
        return cachedItems.get();
    }

    /** Clears the cache (on disconnect, for resource pack changes, etc.). */
    public static void clear() {
        cachedItems.set(null);
        building.set(false);
    }

    // ── Auto-trigger: start building when player joins a world ──────────────

    private static boolean triggered = false;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenOpen(ScreenEvent.Opening event) {
        // Trigger prebuild on any screen open (main menu, pause, etc.)
        if (!triggered) {
            triggered = true;
            prebuildAsync();
        }
    }

    @SubscribeEvent
    public static void onScreenClose(ScreenEvent.Closing event) {
        // If disconnecting (closing the game screen), clear for next session
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            clear();
            triggered = false;
        }
    }
}
