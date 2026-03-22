package com.arcadia.pets;

import com.arcadia.pets.item.PetBagItem;
import com.arcadia.pets.item.PetCollectionBookItem;
import com.arcadia.pets.item.PetItem;
import com.arcadia.pets.item.PetRarity;
import com.arcadia.pets.item.PetSnackItem;
import com.arcadia.pets.item.PetTreatItem;
import com.arcadia.pets.item.StarEssenceItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PetsModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, ArcadiaPets.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArcadiaPets.MOD_ID);

    // --- Items ---

    public static final DeferredHolder<Item, PetItem> PET_ITEM =
            ITEMS.register("pet_item", () -> new PetItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, PetBagItem> COMMON_PET_BAG =
            ITEMS.register("common_pet_bag", () -> new PetBagItem(PetRarity.COMMON, new Item.Properties().stacksTo(4)));

    public static final DeferredHolder<Item, PetBagItem> UNCOMMON_PET_BAG =
            ITEMS.register("uncommon_pet_bag", () -> new PetBagItem(PetRarity.UNCOMMON, new Item.Properties().stacksTo(4)));

    public static final DeferredHolder<Item, PetBagItem> RARE_PET_BAG =
            ITEMS.register("rare_pet_bag", () -> new PetBagItem(PetRarity.RARE, new Item.Properties().stacksTo(4)));

    public static final DeferredHolder<Item, PetBagItem> EPIC_PET_BAG =
            ITEMS.register("epic_pet_bag", () -> new PetBagItem(PetRarity.EPIC, new Item.Properties().stacksTo(4)));

    /** Guaranteed-Legendary bag — fusion output for 5 Epic pets. Rolls Legendary only (Mythic excluded from pool). */
    public static final DeferredHolder<Item, PetBagItem> LEGENDARY_GUARANTEED_BAG =
            ITEMS.register("legendary_guaranteed_bag", () -> new PetBagItem(PetRarity.LEGENDARY, new Item.Properties().stacksTo(4)));

    /**
     * Returns a fresh bag ItemStack to award as fusion output for the given input rarity.
     * Returns {@link net.minecraft.world.item.ItemStack#EMPTY} if the rarity cannot be fused.
     */
    public static net.minecraft.world.item.ItemStack fusionBagFor(PetRarity rarity) {
        return switch (rarity) {
            case COMMON   -> new net.minecraft.world.item.ItemStack(UNCOMMON_PET_BAG.get());
            case UNCOMMON -> new net.minecraft.world.item.ItemStack(RARE_PET_BAG.get());
            case RARE     -> new net.minecraft.world.item.ItemStack(EPIC_PET_BAG.get());
            case EPIC     -> new net.minecraft.world.item.ItemStack(LEGENDARY_GUARANTEED_BAG.get());
            default       -> net.minecraft.world.item.ItemStack.EMPTY;
        };
    }

    public static final DeferredHolder<Item, StarEssenceItem> STAR_ESSENCE =
            ITEMS.register("star_essence", () -> new StarEssenceItem(new Item.Properties().stacksTo(4)));

    public static final DeferredHolder<Item, PetTreatItem> PET_TREAT =
            ITEMS.register("pet_treat", () -> new PetTreatItem(new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, PetSnackItem> PET_SNACK =
            ITEMS.register("pet_snack", () -> new PetSnackItem(new Item.Properties().stacksTo(32)));

    /** A permanent key item that opens the Arcadia dashboard (Pets tab) on right-click. */
    public static final DeferredHolder<Item, PetCollectionBookItem> PET_COLLECTION_BOOK =
            ITEMS.register("pet_collection_book", () -> new PetCollectionBookItem(new Item.Properties().stacksTo(1)));

    // --- Creative Tab ---

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ARCADIA_TAB =
            CREATIVE_TABS.register("arcadia_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.arcadia_prestige.main"))
                    .icon(() -> PET_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(PET_ITEM.get());
                        output.accept(COMMON_PET_BAG.get());
                        output.accept(UNCOMMON_PET_BAG.get());
                        output.accept(RARE_PET_BAG.get());
                        output.accept(EPIC_PET_BAG.get());
                        output.accept(LEGENDARY_GUARANTEED_BAG.get());
                        output.accept(STAR_ESSENCE.get());
                        output.accept(PET_TREAT.get());
                        output.accept(PET_SNACK.get());
                        output.accept(PET_COLLECTION_BOOK.get());
                    })
                    .build());
}
