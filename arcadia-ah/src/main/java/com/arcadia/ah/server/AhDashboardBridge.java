package com.arcadia.ah.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Decouples arcadia-ah commands from arcadia-prestige's DashboardMenu.
 * arcadia-prestige registers the opener at startup; arcadia-ah calls it.
 */
public final class AhDashboardBridge {

    private static Consumer<ServerPlayer> ahTabOpener;

    private AhDashboardBridge() {}

    public static void register(Consumer<ServerPlayer> opener) {
        ahTabOpener = opener;
    }

    public static void openAhTab(ServerPlayer player) {
        if (ahTabOpener != null) ahTabOpener.accept(player);
    }
}
