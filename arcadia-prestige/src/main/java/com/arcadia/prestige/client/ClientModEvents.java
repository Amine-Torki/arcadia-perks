package com.arcadia.prestige.client;

import com.arcadia.prestige.ArcadiaDashboard;
import com.arcadia.prestige.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-side MOD-bus events for arcadia-prestige ONLY.
 * Hub keybind (L) is now in arcadia-lib (LibClientModEvents).
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = ArcadiaDashboard.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.DASHBOARD_MENU.get(), DashboardScreen::new);
    }
}
