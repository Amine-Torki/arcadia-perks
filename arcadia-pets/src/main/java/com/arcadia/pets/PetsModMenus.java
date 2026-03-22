package com.arcadia.pets;

import com.arcadia.pets.server.FusionMenu;
import com.arcadia.pets.server.PetHistoryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PetsModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ArcadiaPets.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<FusionMenu>> FUSION_MENU =
            MENUS.register("fusion_menu", () -> new MenuType<>(FusionMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<PetHistoryMenu>> PET_HISTORY_MENU =
            MENUS.register("pet_history_menu", () -> new MenuType<>(PetHistoryMenu::new, FeatureFlags.VANILLA_SET));
}
