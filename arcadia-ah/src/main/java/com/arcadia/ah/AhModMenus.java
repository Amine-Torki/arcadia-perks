package com.arcadia.ah;

import com.arcadia.ah.server.AhLeaderboardMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AhModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ArcadiaAH.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<AhLeaderboardMenu>> AH_LEADERBOARD_MENU =
            MENUS.register("ah_leaderboard_menu", () -> new MenuType<>(AhLeaderboardMenu::new, FeatureFlags.VANILLA_SET));
}
