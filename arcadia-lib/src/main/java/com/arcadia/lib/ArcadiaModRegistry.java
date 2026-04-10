package com.arcadia.lib;

import com.arcadia.lib.dashboard.DashboardTabHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Central service registry for all Arcadia modules. Lives in arcadia-lib so
 * every module can register and discover features without depending on each other.
 *
 * <p>Modules call the register methods during {@code FMLCommonSetupEvent}.
 * The hub/dashboard queries the registry at runtime to discover available tabs.</p>
 */
public final class ArcadiaModRegistry {

    // ── Tab openers (mod → opener callback) ──────────────────────────────────
    private static final Map<Integer, Consumer<ServerPlayer>> tabOpeners = new ConcurrentHashMap<>();
    private static final Map<Integer, Supplier<DashboardTabHandler>> tabFactories = new ConcurrentHashMap<>();

    // ── Hub opener (registered by prestige) ──────────────────────────────────
    private static Consumer<ServerPlayer> hubOpener;

    // ── AH-specific: search refresher ────────────────────────────────────────
    private static Consumer<ServerPlayer> searchRefresher;

    private ArcadiaModRegistry() {}

    // ── Hub ──────────────────────────────────────────────────────────────────

    /** Registered by the dashboard mod to allow any mod to open the main hub. */
    public static void registerHubOpener(Consumer<ServerPlayer> opener) {
        hubOpener = opener;
    }

    /** Opens the Arcadia hub for a player. */
    public static void openHub(ServerPlayer player) {
        if (hubOpener != null) hubOpener.accept(player);
    }

    public static boolean isHubAvailable() { return hubOpener != null; }

    // ── Tab openers ─────────────────────────────────────────────────────────

    /** Register a callback that opens the dashboard at a specific tab index. */
    public static void registerTabOpener(int tabIndex, Consumer<ServerPlayer> opener) {
        tabOpeners.put(tabIndex, opener);
    }

    /** Opens the dashboard at the given tab for a player. */
    public static void openTab(ServerPlayer player, int tabIndex) {
        Consumer<ServerPlayer> opener = tabOpeners.get(tabIndex);
        if (opener != null) opener.accept(player);
    }

    public static boolean isTabAvailable(int tabIndex) { return tabOpeners.containsKey(tabIndex); }

    // ── Tab handler factories ───────────────────────────────────────────────

    /** Register a factory for a dashboard tab handler (used by the dashboard menu). */
    public static void registerTabHandler(int tabIndex, Supplier<DashboardTabHandler> factory) {
        tabFactories.put(tabIndex, factory);
    }

    /** Returns the factory for a tab handler, or null if not registered. */
    public static Supplier<DashboardTabHandler> getTabHandler(int tabIndex) {
        return tabFactories.get(tabIndex);
    }

    // ── AH search refresher ─────────────────────────────────────────────────

    public static void registerSearchRefresher(Consumer<ServerPlayer> refresher) {
        searchRefresher = refresher;
    }

    public static void notifySearchUpdated(ServerPlayer player) {
        if (searchRefresher != null) searchRefresher.accept(player);
    }
}
