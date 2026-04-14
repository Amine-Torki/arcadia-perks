package com.arcadia.ah;

import com.arcadia.ah.auction.AuctionDatabase;
import com.arcadia.ah.config.AhConfig;
import com.arcadia.ah.network.AhPacketHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

@Mod("arcadia_ah")
public final class ArcadiaAH {
    public static final String MOD_ID = "arcadia_ah";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArcadiaAH(IEventBus modBus, ModContainer container) {
        AhModMenus.MENUS.register(modBus);

        modBus.addListener(AhPacketHandler::onRegisterPayloadHandlers);
        modBus.addListener(this::onConfigLoad);
        modBus.addListener(this::onCommonSetup);

        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);

        container.registerConfig(ModConfig.Type.SERVER, AhConfig.SPEC, "arcadia/ah/ah.toml");
    }

    private void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        // Register database tables
        com.arcadia.lib.data.DatabaseManager.registerTables(new com.arcadia.ah.auction.AuctionTableDefinition());

        // Register AH tab handler via the central lib registry
        com.arcadia.lib.ArcadiaModRegistry.registerTabHandler(3,
                com.arcadia.ah.server.AhDashboardTab::new);

        // Register server-side actions
        com.arcadia.lib.ArcadiaModRegistry.registerServerAction("ah.refresh_cache",
                p -> com.arcadia.ah.auction.AuctionManager.refreshCache());
        com.arcadia.lib.ArcadiaModRegistry.registerServerActionWithPayload("ah.clear_search",
                (p, unused) -> com.arcadia.ah.auction.AuctionManager.clearSearch(p.getUUID()));

        // Register hub card so the Arcadia Hub displays the AH module
        com.arcadia.lib.ArcadiaModRegistry.registerCard(
                new com.arcadia.lib.client.ArcadiaModCard("ah", "★",
                        "arcadia_ah.hub.auction.label", "arcadia_ah.hub.auction.sub",
                        0xB87333, 3, 1, true));
        LOGGER.info("[ArcadiaAH] Registered AH tab in ArcadiaModRegistry.");
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        // Tables are now created centrally by DatabaseManager via AuctionTableDefinition
        LOGGER.info("[ArcadiaAH] Server starting. DB active: {}",
                com.arcadia.lib.data.DatabaseManager.isDatabaseActive());
    }

    private void onConfigLoad(ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) return;
        if (event.getConfig().getSpec() == AhConfig.SPEC) {
            AhConfig.apply();
            LOGGER.info("[ArcadiaAH] Config loaded (listing_duration_hours: {}, max_listings: {}).",
                    AhConfig.LISTING_DURATION_HOURS.get(), AhConfig.MAX_LISTINGS_PER_PLAYER_V);
        }
    }
}
