package com.arcadia.ah;

import com.arcadia.ah.item.ArcadiaTokenItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AhModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, ArcadiaAH.MOD_ID);

    public static final DeferredHolder<Item, ArcadiaTokenItem> ARCADIA_TOKEN =
            ITEMS.register("arcadia_token", () -> new ArcadiaTokenItem(new Item.Properties().stacksTo(64)));
}
