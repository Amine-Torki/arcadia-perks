package com.arcadia.pets.client;

import com.arcadia.pets.ArcadiaPets;
import com.arcadia.pets.PetsModItems;
import com.arcadia.pets.PetsModMenus;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-side MOD-bus event registrations for arcadia-pets.
 * Screens, keybinds, and renderers are registered here (not in prestige).
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = ArcadiaPets.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PetsClientModEvents {

    private PetsClientModEvents() {}

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
        event.register(PetsModMenus.FUSION_MENU.get(), FusionScreen::new);
        event.register(PetsModMenus.PET_HISTORY_MENU.get(), PetHistoryScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PetKeyHandler.OPEN_PET_PANEL);
    }
}
