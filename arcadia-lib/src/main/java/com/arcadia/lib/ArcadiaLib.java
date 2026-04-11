package com.arcadia.lib;

import com.arcadia.lib.config.DatabaseConfig;
import com.arcadia.lib.economy.EconomyConfig;
import com.arcadia.lib.network.ArcadiaLibNet;
import com.arcadia.lib.permissions.PermissionConfig;
import com.arcadia.lib.staff.StaffConfig;
import com.arcadia.lib.staff.StaffService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod("arcadia_lib")
public final class ArcadiaLib {
    public static final String MOD_ID = "arcadia_lib";

    public ArcadiaLib(IEventBus modBus, ModContainer container) {
        LibModItems.ITEMS.register(modBus);

        // Register configs
        container.registerConfig(ModConfig.Type.SERVER, PermissionConfig.SPEC, "arcadia/lib/permissions.toml");
        container.registerConfig(ModConfig.Type.SERVER, StaffConfig.SPEC, "arcadia/lib/staff.toml");
        container.registerConfig(ModConfig.Type.SERVER, EconomyConfig.SPEC, "arcadia/lib/economy.toml");
        container.registerConfig(ModConfig.Type.SERVER, DatabaseConfig.SPEC, "arcadia/lib/database.toml");
        modBus.addListener(this::onConfigLoad);

        // Register network payloads
        modBus.addListener(ArcadiaLibNet::registerPayloads);

        // Sync staff role to client on login
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
    }

    private void onConfigLoad(ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) return;
        if (event.getConfig().getSpec() == PermissionConfig.SPEC) {
            PermissionConfig.apply();
        }
        if (event.getConfig().getSpec() == StaffConfig.SPEC) {
            StaffConfig.apply();
        }
        if (event.getConfig().getSpec() == EconomyConfig.SPEC) {
            EconomyConfig.apply();
        }
        if (event.getConfig().getSpec() == DatabaseConfig.SPEC) {
            DatabaseConfig.apply();
        }
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        int roleLevel = StaffService.getRole(sp).getLevel();
        ArcadiaLibNet.sendStaffSync(sp, roleLevel);
    }
}
