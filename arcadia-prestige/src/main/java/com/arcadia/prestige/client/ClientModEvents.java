package com.arcadia.prestige.client;

import com.arcadia.prestige.ArcadiaDashboard;
import com.arcadia.pets.PetsModItems;
import com.arcadia.pets.PetsModMenus;
import com.arcadia.pets.client.FusionScreen;
import com.arcadia.pets.client.PetHistoryScreen;
import com.arcadia.pets.client.PetItemRenderer;
import com.arcadia.ah.AhModMenus;
import com.arcadia.ah.client.AhLeaderboardScreen;
import com.arcadia.pets.client.PetKeyHandler;
import com.arcadia.prestige.ModMenus;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Single registration point for all client-side MOD-bus events.
 * Screens and keybinds are registered here instead of inner classes scattered across screen files.
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = ArcadiaDashboard.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return PetItemRenderer.INSTANCE;
            }
        }, PetsModItems.PET_ITEM.get());
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.DASHBOARD_MENU.get(), DashboardScreen::new);
        event.register(PetsModMenus.FUSION_MENU.get(), FusionScreen::new);
        event.register(PetsModMenus.PET_HISTORY_MENU.get(), PetHistoryScreen::new);
        event.register(AhModMenus.AH_LEADERBOARD_MENU.get(), AhLeaderboardScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PetKeyHandler.OPEN_PET_PANEL);
    }
}
