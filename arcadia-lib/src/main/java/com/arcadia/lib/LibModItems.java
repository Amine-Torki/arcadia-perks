package com.arcadia.lib;

import com.arcadia.lib.item.ArcadiaTokenItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LibModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, ArcadiaLib.MOD_ID);

    public static final DeferredHolder<Item, ArcadiaTokenItem> ARCADIA_TOKEN =
            ITEMS.register("arcadia_token", () -> new ArcadiaTokenItem(new Item.Properties().stacksTo(64)));

    private LibModItems() {}
}
