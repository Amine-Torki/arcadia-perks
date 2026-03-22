package com.arcadia.prestige;

import com.arcadia.prestige.server.DashboardMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ArcadiaDashboard.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<DashboardMenu>> DASHBOARD_MENU =
            MENUS.register("dashboard_menu", () -> new MenuType<>(DashboardMenu::new, FeatureFlags.VANILLA_SET));
}
