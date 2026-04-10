package com.arcadia.pets;

import com.arcadia.pets.config.PetPoolConfig;
import com.arcadia.pets.network.PetPacketHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

@Mod("arcadia_pets")
public final class ArcadiaPets {
    public static final String MOD_ID = "arcadia_pets";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArcadiaPets(IEventBus modBus, ModContainer container) {
        PetsModItems.ITEMS.register(modBus);
        PetsModItems.CREATIVE_TABS.register(modBus);
        PetsModMenus.MENUS.register(modBus);

        modBus.addListener(PetPacketHandler::onRegisterPayloadHandlers);
        modBus.addListener(this::onConfigLoad);
        modBus.addListener(this::onCommonSetup);

        container.registerConfig(ModConfig.Type.SERVER, PetPoolConfig.SPEC, "arcadia/pets/pets.toml");
    }

    private void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        // Register database tables
        com.arcadia.lib.data.DatabaseManager.registerTables(new com.arcadia.pets.server.PetsTableDefinition());

        // Register pets tab handler via the central lib registry
        com.arcadia.lib.ArcadiaModRegistry.registerTabHandler(1,
                com.arcadia.pets.server.PetsDashboardTab::new);

        // Register hub card so the Arcadia Hub displays the Pets module
        com.arcadia.lib.ArcadiaModRegistry.registerCard(
                new com.arcadia.lib.client.ArcadiaModCard("pets", "♦",
                        "arcadia_prestige.hub.pets.label", "arcadia_prestige.hub.pets.sub",
                        0x4ECCA3, 1, true));
        LOGGER.info("[ArcadiaPets] Registered pets tab in ArcadiaModRegistry.");
    }

    private void onConfigLoad(ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) return;
        if (event.getConfig().getSpec() == PetPoolConfig.SPEC) {
            PetPoolConfig.applyToRarities();
            LOGGER.info("[ArcadiaPets] Pet pool config loaded.");
        }
    }
}
