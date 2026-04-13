package com.arcadia.lib;

import com.arcadia.lib.client.ArcadiaModCard;
import com.arcadia.lib.dashboard.DashboardTabHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
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

    // ── Hub cards (displayed in ArcadiaHubScreen) ───────────────────────────

    private static final Map<String, ArcadiaModCard> cards = new ConcurrentHashMap<>();

    /**
     * Client-side callback that opens a dashboard tab by index.
     * Registered once by the dashboard mod (prestige) — sends the network packet.
     * Other mods never need to know about prestige's packet system.
     */
    private static IntConsumer clientTabOpener;

    /** Registers a module card to be displayed in the Arcadia Hub. */
    public static void registerCard(ArcadiaModCard card) {
        cards.put(card.id(), card);
    }

    /** Returns all registered cards sorted by display order. */
    public static List<ArcadiaModCard> getCards() {
        List<ArcadiaModCard> sorted = new ArrayList<>(cards.values());
        sorted.sort(Comparator.comparingInt(ArcadiaModCard::sortOrder));
        return sorted;
    }

    /**
     * Registers the client-side tab opener callback. Called once by prestige's
     * client init to wire up the packet sending. The hub screen calls this
     * with the card's sortOrder (= tab index) when a card is clicked.
     */
    public static void registerClientTabOpener(IntConsumer opener) {
        clientTabOpener = opener;
    }

    /** Opens a tab on the client side (sends packet via registered callback). */
    public static void openTabClient(int tabIndex) {
        if (clientTabOpener != null) clientTabOpener.accept(tabIndex);
    }

    // ── Server-side action callbacks (decouples mods from each other) ──────

    private static final Map<String, java.util.function.Consumer<ServerPlayer>> serverActions = new ConcurrentHashMap<>();
    private static final Map<String, java.util.function.BiConsumer<ServerPlayer, String>> serverActionsWithPayload = new ConcurrentHashMap<>();

    /** Registers a named server-side action. Any mod can trigger it by ID. */
    public static void registerServerAction(String actionId, java.util.function.Consumer<ServerPlayer> handler) {
        serverActions.put(actionId, handler);
    }

    /** Registers a server-side action with a string payload parameter. */
    public static void registerServerActionWithPayload(String actionId, java.util.function.BiConsumer<ServerPlayer, String> handler) {
        serverActionsWithPayload.put(actionId, handler);
    }

    /**
     * Executes a registered server-side action.
     * Supports "actionId:payload" format — splits on first ':' and passes payload to handler.
     */
    public static void executeServerAction(String actionIdOrCompound, ServerPlayer player) {
        // Try exact match first
        var handler = serverActions.get(actionIdOrCompound);
        if (handler != null) { handler.accept(player); return; }

        // Try "prefix:payload" format
        int colon = actionIdOrCompound.indexOf(':');
        if (colon > 0) {
            String prefix = actionIdOrCompound.substring(0, colon);
            String payload = actionIdOrCompound.substring(colon + 1);
            var payloadHandler = serverActionsWithPayload.get(prefix);
            if (payloadHandler != null) { payloadHandler.accept(player, payload); return; }
            // Also try simple action with prefix only
            var simpleHandler = serverActions.get(prefix);
            if (simpleHandler != null) simpleHandler.accept(player);
        }
    }

    /** Checks if a server action is registered. */
    public static boolean hasServerAction(String actionId) {
        return serverActions.containsKey(actionId) || serverActionsWithPayload.containsKey(actionId);
    }

    // ── Hub card click handlers (standalone actions, bypass tab system) ────

    private static final Map<String, Runnable> cardClickHandlers = new ConcurrentHashMap<>();

    /** Registers a click handler for a hub card by card ID. Bypasses the tab system. */
    public static void registerCardClickHandler(String cardId, Runnable handler) {
        cardClickHandlers.put(cardId, handler);
    }

    /** Returns the click handler for a card, or null if it should use the default tab system. */
    public static Runnable getCardClickHandler(String cardId) {
        return cardClickHandlers.get(cardId);
    }

    // ── Client-side action callbacks ────────────────────────────────────────

    private static final Map<String, Runnable> clientActions = new ConcurrentHashMap<>();

    /** Registers a named client-side action (e.g. opening a specific screen). */
    public static void registerClientAction(String actionId, Runnable handler) {
        clientActions.put(actionId, handler);
    }

    /** Executes a registered client-side action. */
    public static void executeClientAction(String actionId) {
        var handler = clientActions.get(actionId);
        if (handler != null) handler.run();
    }

    // ── Reward item registry (decouples reward systems from item mods) ──────

    private static final Map<String, java.util.function.Supplier<net.minecraft.world.item.ItemStack>> rewardItems = new ConcurrentHashMap<>();

    /** Registers a reward item factory. Used by daily rewards, etc. */
    public static void registerRewardItem(String itemId, java.util.function.Supplier<net.minecraft.world.item.ItemStack> factory) {
        rewardItems.put(itemId, factory);
    }

    /** Creates a reward item by ID. Returns EMPTY if not registered. */
    public static net.minecraft.world.item.ItemStack createRewardItem(String itemId) {
        var factory = rewardItems.get(itemId);
        return factory != null ? factory.get() : net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** Creates a reward item with a specific count. */
    public static net.minecraft.world.item.ItemStack createRewardItem(String itemId, int count) {
        var stack = createRewardItem(itemId);
        if (!stack.isEmpty()) stack.setCount(count);
        return stack;
    }

    // ── Screen/menu registration registry (for client-side menu registrations) ──

    private static final Map<String, Runnable> menuRegistrations = new ConcurrentHashMap<>();

    /** Registers a menu screen registration callback. Called during RegisterMenuScreensEvent. */
    public static void registerMenuScreenInit(String modId, Runnable registrar) {
        menuRegistrations.put(modId, registrar);
    }

    /** Returns all registered menu screen initializers. */
    public static java.util.Collection<Runnable> getMenuRegistrations() {
        return menuRegistrations.values();
    }

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

    // ── Pet item display (for ELO leaderboard — avoids cross-module imports) ─

    private static java.util.function.BiFunction<java.util.UUID, String, net.minecraft.world.item.ItemStack> petItemProvider;

    /** Registered by arcadia-pets to allow other mods to get a pet item for display. */
    public static void registerPetItemProvider(java.util.function.BiFunction<java.util.UUID, String, net.minecraft.world.item.ItemStack> provider) {
        petItemProvider = provider;
    }

    /** Returns a copy of the pet item matching mobType from the player's collection, or EMPTY. */
    public static net.minecraft.world.item.ItemStack getPetItemForDisplay(java.util.UUID playerUuid, String mobType) {
        if (petItemProvider == null) return net.minecraft.world.item.ItemStack.EMPTY;
        return petItemProvider.apply(playerUuid, mobType);
    }
}
