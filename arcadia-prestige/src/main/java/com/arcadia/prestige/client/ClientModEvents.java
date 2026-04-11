package com.arcadia.prestige.client;

import com.arcadia.prestige.ArcadiaDashboard;
import com.arcadia.prestige.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-side MOD-bus events for arcadia-prestige ONLY.
 * Pet/AH screens and keybinds are now registered by their own mods.
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = ArcadiaDashboard.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        // Only prestige's own screen — pets and ah register theirs in their own mods
        event.register(ModMenus.DASHBOARD_MENU.get(), DashboardScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // Only prestige's own keybind — pet keybind registered by arcadia-pets
        event.register(HubKeyHandler.OPEN_HUB);
    }
}
