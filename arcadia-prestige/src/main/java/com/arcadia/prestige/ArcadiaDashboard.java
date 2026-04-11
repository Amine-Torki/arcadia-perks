package com.arcadia.prestige;

import com.arcadia.lib.config.DatabaseConfig;
import com.arcadia.lib.data.DatabaseManager;
import com.arcadia.lib.DebugMode;
import com.arcadia.prestige.config.PrestigeConfig;
import com.arcadia.prestige.network.PacketHandler;
import com.arcadia.prestige.server.DashboardMenu;
import com.arcadia.prestige.server.CosmeticPermissionScanner;
// LuckPermsHook moved to lib as LuckPermsBackend — initialized via PermissionService
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
        // Register database tables for this module
        com.arcadia.lib.data.DatabaseManager.registerTables(new com.arcadia.prestige.server.PrestigeCoreTableDefinition());

        // Register hub opener so /arcadia and other mods can open the hub
        com.arcadia.lib.ArcadiaModRegistry.registerHubOpener(p -> {
            PacketHandler.sendToPlayer(p, new com.arcadia.prestige.network.S2COpenHub());
        });

        // Register tab openers for cosmetics and daily (always available)
        com.arcadia.lib.ArcadiaModRegistry.registerTabOpener(0, p -> DashboardMenu.openFor(p, 0));
        com.arcadia.lib.ArcadiaModRegistry.registerTabOpener(2, p -> DashboardMenu.openFor(p, 2));

        // Search refresher for AH (reopen dashboard at tab 3 after search)
        com.arcadia.lib.ArcadiaModRegistry.registerSearchRefresher(p -> DashboardMenu.openFor(p, 3));

        // Discover pet/ah tab handlers registered by their respective mods
        var petsFactory = com.arcadia.lib.ArcadiaModRegistry.getTabHandler(1);
        if (petsFactory != null) {
            DashboardMenu.registerPetsHandler(petsFactory);
            com.arcadia.lib.ArcadiaModRegistry.registerTabOpener(1, p -> DashboardMenu.openFor(p, 1));
        }

        var ahFactory = com.arcadia.lib.ArcadiaModRegistry.getTabHandler(3);
        if (ahFactory != null) {
            DashboardMenu.registerAhHandler(ahFactory);
            com.arcadia.lib.ArcadiaModRegistry.registerTabOpener(3, p -> DashboardMenu.openFor(p, 3));
        }

        // Register hub cards for prestige's own tabs (cosmetics + daily)
        com.arcadia.lib.ArcadiaModRegistry.registerCard(
                new com.arcadia.lib.client.ArcadiaModCard("cosmetics", "✨",
                        "arcadia_prestige.hub.cosmetics.label", "arcadia_prestige.hub.cosmetics.sub",
                        0x6BB8D4, 0, true));
        com.arcadia.lib.ArcadiaModRegistry.registerCard(
                new com.arcadia.lib.client.ArcadiaModCard("daily", "⭐",
                        "arcadia_prestige.hub.daily.label", "arcadia_prestige.hub.daily.sub",
                        0xD4A847, 2, true));

        // Register generic client-side tab opener (sends the C2SDashboardAction packet)
        com.arcadia.lib.ArcadiaModRegistry.registerClientTabOpener(tabIndex ->
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.arcadia.prestige.network.C2SDashboardAction(
                                com.arcadia.prestige.network.C2SDashboardAction.OPEN_TAB,
                                String.valueOf(tabIndex))));
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        boolean isDedicated = event.getServer().isDedicatedServer();
        DatabaseManager.initialize(isDedicated);
        com.arcadia.lib.data.PlayerDataHandler.setServer(event.getServer());
        com.arcadia.lib.permissions.PermissionService.init(
                com.arcadia.lib.permissions.LuckPermsBackend.createOrFallback());
        CosmeticPermissionScanner.init();
        LOGGER.info("[ArcadiaPrestige] Server initialized. Dedicated: {}, DB active: {}, Debug: {}",
                isDedicated, DatabaseManager.isDatabaseActive(), DebugMode.ENABLED);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        DatabaseManager.shutdown();
        com.arcadia.lib.permissions.PermissionService.shutdown();
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
