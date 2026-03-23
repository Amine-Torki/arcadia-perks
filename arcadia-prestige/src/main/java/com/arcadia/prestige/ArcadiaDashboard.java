package com.arcadia.prestige;

import com.arcadia.ah.server.AhDashboardBridge;
import com.arcadia.lib.config.DatabaseConfig;
import com.arcadia.lib.data.DatabaseManager;
import com.arcadia.lib.DebugMode;
import com.arcadia.pets.server.DashboardMenuBridge;
import com.arcadia.prestige.network.PacketHandler;
import com.arcadia.prestige.server.DashboardMenu;
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

        container.registerConfig(ModConfig.Type.SERVER, DatabaseConfig.SPEC, "arcadia-database.toml");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        DashboardMenuBridge.register(player -> DashboardMenu.openFor(player, 1));
        AhDashboardBridge.register(player -> DashboardMenu.openFor(player, 3));
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        DatabaseManager.initialize();
        LuckPermsHook.init();
        LOGGER.info("[ArcadiaPrestige] Server initialized. Debug mode: {}", DebugMode.ENABLED);
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
    }
}
