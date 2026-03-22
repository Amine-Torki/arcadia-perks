package com.arcadia.pets;

import com.arcadia.pets.config.PetPoolConfig;
import com.arcadia.pets.network.PetPacketHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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

        container.registerConfig(ModConfig.Type.SERVER, PetPoolConfig.SPEC, "arcadia-pets.toml");
    }
}
