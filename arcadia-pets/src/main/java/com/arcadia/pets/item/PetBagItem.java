package com.arcadia.pets.item;

import com.arcadia.pets.PetsModItems;
import com.arcadia.lib.data.DatabaseManager;
import com.arcadia.lib.data.PlayerDataHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import com.arcadia.pets.network.S2CPetReveal;
import com.arcadia.pets.server.PetCollectionSavedData;
import com.arcadia.pets.server.PetHistorySavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An openable bag item that triggers a pet reveal animation and grants a random pet.
 */
public class PetBagItem extends Item {

    /** Maps dropped ItemEntity UUID → owner player UUID. Only the owner can pick up the item. */
    public static final Map<UUID, UUID> LOCKED_DROPS = new HashMap<>();

    private final PetRarity minimumRarity;

    public PetBagItem(PetRarity minimumRarity, Properties properties) {
        super(properties);
        this.minimumRarity = minimumRarity;
    }

    public PetRarity getMinimumRarity() {
        return minimumRarity;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {

            // Roll a new pet and build the item stack.
            PetData pet = PetRoller.roll(minimumRarity);
            ItemStack petStack = new ItemStack(PetsModItems.PET_ITEM.get());
            pet.applyToStack(petStack);
            PetHistorySavedData.getOrCreate(serverPlayer.getServer()).log(serverPlayer.getUUID(), pet.toTag());

            // Send reveal packet to client
            PacketDistributor.sendToPlayer(serverPlayer, new S2CPetReveal(pet.toTag(), (byte) minimumRarity.ordinal()));

            // Delivery priority: collection → inventory → ground.
            // We try collection first so the pet never risks being voided by a full
            // inventory. The bag is still in hand at this point so inventory.add()
            // cannot place the pet into the hand slot.
            PetCollectionSavedData colData = PetCollectionSavedData.getOrCreate(serverPlayer.getServer());
            if (colData.deposit(serverPlayer.getUUID(), petStack)) {
                serverPlayer.sendSystemMessage(Component.translatable("arcadia_prestige.gui.pet_bag.sent_to_collection")
                        .withStyle(ChatFormatting.GREEN));
            } else if (player.getInventory().add(petStack)) {
                serverPlayer.sendSystemMessage(Component.translatable("arcadia_prestige.gui.pet_bag.collection_full_to_inv")
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                // Both full — drop on ground as last resort; lock to this player only
                net.minecraft.world.entity.item.ItemEntity dropped = player.drop(petStack, false);
                if (dropped != null) {
                    LOCKED_DROPS.put(dropped.getUUID(), serverPlayer.getUUID());
                }
                serverPlayer.sendSystemMessage(Component.translatable("arcadia_prestige.gui.pet_bag.all_full")
                        .withStyle(ChatFormatting.RED));
            }

            // Consume the bag AFTER placing the pet so the engine's return-value
            // processing never overwrites the pet in the (now freed) hand slot.
            stack.shrink(1);

            // Register pet in database
            DatabaseManager.executeAsync(() -> PlayerDataHandler.registerPet(pet.petId(), pet.mobType(), pet.rarity().ordinal(), pet.totalStars()));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("arcadia_prestige.gui.pet_bag.min_rarity").withStyle(ChatFormatting.GRAY)
                .append(minimumRarity.getTranslatableName()));

        tooltipComponents.add(Component.empty());
        tooltipComponents.add(Component.translatable("arcadia_prestige.gui.pet_bag.loot_chances").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        PetRarity[] rarities = PetRarity.values();
        int totalWeight = 0;
        for (PetRarity r : rarities) {
            if (r.ordinal() >= minimumRarity.ordinal()) {
                totalWeight += r.getWeight();
            }
        }

        if (totalWeight > 0) {
            for (PetRarity r : rarities) {
                if (r.ordinal() >= minimumRarity.ordinal()) {
                    float chance = (float) r.getWeight() / totalWeight * 100f;
                    tooltipComponents.add(Component.translatable("arcadia_prestige.gui.pet_bag.chance_format",
                            r.getTranslatableName(), String.format("%.1f", chance)));
                }
            }
        }

        tooltipComponents.add(Component.empty());
        tooltipComponents.add(Component.translatable("arcadia_prestige.gui.pet_bag.click_to_open").withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId()).withStyle(minimumRarity.getColor());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return minimumRarity.ordinal() >= PetRarity.EPIC.ordinal();
    }
}
