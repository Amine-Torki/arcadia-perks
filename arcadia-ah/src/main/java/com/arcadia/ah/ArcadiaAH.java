package com.arcadia.ah;

import com.arcadia.ah.auction.AuctionDatabase;
import com.arcadia.ah.network.AhPacketHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

@Mod("arcadia_ah")
public final class ArcadiaAH {
    public static final String MOD_ID = "arcadia_ah";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArcadiaAH(IEventBus modBus) {
        AhModMenus.MENUS.register(modBus);

        modBus.addListener(AhPacketHandler::onRegisterPayloadHandlers);

        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        AuctionDatabase.createTables();
        LOGGER.info("[ArcadiaAH] Auction tables verified.");
    }
}
