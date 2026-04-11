package com.arcadia.ah.client;

import com.arcadia.ah.AhModMenus;
import com.arcadia.ah.ArcadiaAH;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-side MOD-bus event registrations for arcadia-ah.
 * Screens are registered here (not in prestige).
 */
@EventBusSubscriber(modid = ArcadiaAH.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AhClientModEvents {

    private AhClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(AhModMenus.AH_LEADERBOARD_MENU.get(), AhLeaderboardScreen::new);
    }
}
