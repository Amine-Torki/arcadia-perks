package com.arcadia.prestige;

import com.arcadia.lib.config.DatabaseConfig;
import com.arcadia.lib.data.DatabaseManager;
import com.arcadia.lib.DebugMode;
import com.arcadia.prestige.config.PrestigeConfig;
import com.arcadia.prestige.network.PacketHandler;
import com.arcadia.prestige.server.DashboardMenu;
import com.arcadia.prestige.server.CosmeticPermissionScanner;
import com.arcadia.prestige.server.LuckPermsHook;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(ArcadiaDashboard.MOD_ID)
public class ArcadiaDashboard {

    public static final String MOD_ID = "arcadia_prestige";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArcadiaDashboard(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);

        modBus.addListener(PacketHandler::onRegisterPayloadHandlers);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onConfigLoad);

        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        container.registerConfig(ModConfig.Type.SERVER, DatabaseConfig.SPEC, "arcadia/lib/database.toml");
        container.registerConfig(ModConfig.Type.SERVER, PrestigeConfig.SPEC, "arcadia/prestige/prestige.toml");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        if (net.neoforged.fml.ModList.get().isLoaded("arcadia_pets")) PetsModuleSetup.init();
        if (net.neoforged.fml.ModList.get().isLoaded("arcadia_ah"))   AhModuleSetup.init();
    }

    /**
     * Inner class — only loaded/instantiated when arcadia-pets is confirmed present.
     * Java does not load inner classes until they are first referenced at runtime.
     */
    private static final class PetsModuleSetup {
        static void init() {
            com.arcadia.pets.server.DashboardMenuBridge.register(p -> DashboardMenu.openFor(p, 1));
            DashboardMenu.registerPetsHandler(com.arcadia.pets.server.PetsDashboardTab::new);
        }
    }

    /**
     * Inner class — only loaded/instantiated when arcadia-ah is confirmed present.
     */
    private static final class AhModuleSetup {
        static void init() {
            com.arcadia.ah.server.AhDashboardBridge.register(p -> DashboardMenu.openFor(p, 3));
            // Reopen the dashboard at tab 3 after search — the client closed the search screen
            // and needs the dashboard screen to come back.
            com.arcadia.ah.server.AhDashboardBridge.registerSearchRefresher(
                sp -> DashboardMenu.openFor(sp, 3));
            DashboardMenu.registerAhHandler(com.arcadia.ah.server.AhDashboardTab::new);
        }
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        boolean isDedicated = event.getServer().isDedicatedServer();
        DatabaseManager.initialize(isDedicated);
        LuckPermsHook.init();
        CosmeticPermissionScanner.init();
        LOGGER.info("[ArcadiaPrestige] Server initialized. Dedicated: {}, DB active: {}, Debug: {}",
                isDedicated, DatabaseManager.isDatabaseActive(), DebugMode.ENABLED);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        DatabaseManager.shutdown();
    }

    private void onConfigLoad(ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) return; // config values unavailable during unload
        if (event.getConfig().getSpec() == DatabaseConfig.SPEC) {
            DatabaseConfig.apply();
            LOGGER.info("[ArcadiaPrestige] Database config loaded (host: {}, db: {}).",
                    com.arcadia.lib.config.DatabaseConfig.DB_HOST,
                    com.arcadia.lib.config.DatabaseConfig.DB_NAME);
        }
        if (event.getConfig().getSpec() == PrestigeConfig.SPEC) {
            PrestigeConfig.apply();
            LOGGER.info("[ArcadiaPrestige] Prestige config loaded (server_id: {}, perm_vip: {}).",
                    PrestigeConfig.CACHED_SERVER_ID, PrestigeConfig.GRADE_PERM_VIP);
        }
    }
}
