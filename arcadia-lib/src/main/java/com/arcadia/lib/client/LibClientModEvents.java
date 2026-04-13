package com.arcadia.lib.client;

import com.arcadia.lib.ArcadiaLib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Client-side MOD-bus registrations for arcadia-lib.
 * Registers the hub keybind (L key).
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = ArcadiaLib.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LibClientModEvents {

    private LibClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(HubKeyHandler.OPEN_HUB);
    }
}
