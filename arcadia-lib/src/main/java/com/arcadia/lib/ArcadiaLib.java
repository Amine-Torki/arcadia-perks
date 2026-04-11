package com.arcadia.lib;

import com.arcadia.lib.permissions.PermissionConfig;
import com.arcadia.lib.staff.StaffConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

@Mod("arcadia_lib")
public final class ArcadiaLib {
    public static final String MOD_ID = "arcadia_lib";

    public ArcadiaLib(IEventBus modBus, ModContainer container) {
        LibModItems.ITEMS.register(modBus);

        // Register configs
        container.registerConfig(ModConfig.Type.SERVER, PermissionConfig.SPEC, "arcadia/lib/permissions.toml");
        container.registerConfig(ModConfig.Type.SERVER, StaffConfig.SPEC, "arcadia/lib/staff.toml");
        modBus.addListener(this::onConfigLoad);
    }

    private void onConfigLoad(ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) return;
        if (event.getConfig().getSpec() == PermissionConfig.SPEC) {
            PermissionConfig.apply();
        }
        if (event.getConfig().getSpec() == StaffConfig.SPEC) {
            StaffConfig.apply();
        }
    }
}
