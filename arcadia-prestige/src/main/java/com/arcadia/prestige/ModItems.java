package com.arcadia.prestige;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Prestige-specific item registrations.
 * Pet items are in arcadia-pets (PetsModItems), AH items in arcadia-ah (AhModItems).
 */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, ArcadiaDashboard.MOD_ID);

    // Prestige-specific items (cosmetics, etc.) registered here.
    // Currently empty — items belong to their respective modules.
}
