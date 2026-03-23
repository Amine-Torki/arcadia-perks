package com.arcadia.ah.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Decouples arcadia-ah commands from arcadia-prestige's DashboardMenu.
 * arcadia-prestige registers the opener at startup; arcadia-ah calls it.
 */
public final class AhDashboardBridge {

    private static Consumer<ServerPlayer> ahTabOpener;
    private static Consumer<ServerPlayer> searchRefresher;

    private AhDashboardBridge() {}

    public static void register(Consumer<ServerPlayer> opener) {
        ahTabOpener = opener;
    }

    public static void openAhTab(ServerPlayer player) {
        if (ahTabOpener != null) ahTabOpener.accept(player);
    }

    /** Called by C2SAhSearch after the player submits a new search query. */
    public static void registerSearchRefresher(Consumer<ServerPlayer> refresher) {
        searchRefresher = refresher;
    }

    public static void notifySearchUpdated(ServerPlayer player) {
        if (searchRefresher != null) searchRefresher.accept(player);
    }
}
