package com.arcadia.pets.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Decouples arcadia-pets commands from arcadia-prestige's DashboardMenu.
 * arcadia-prestige registers the opener at startup; arcadia-pets calls it.
 */
public final class DashboardMenuBridge {

    private static Consumer<ServerPlayer> petsTabOpener;

    private DashboardMenuBridge() {}

    public static void register(Consumer<ServerPlayer> opener) {
        petsTabOpener = opener;
    }

    public static void openPetsTab(ServerPlayer player) {
        if (petsTabOpener != null) petsTabOpener.accept(player);
    }
}
