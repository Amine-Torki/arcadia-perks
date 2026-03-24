package com.arcadia.pets.item;

import com.arcadia.pets.server.PetManager;
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

import java.util.List;

/**
 * Right-clicking opens the Arcadia dashboard directly on the Pets tab,
 * so players can manage their collection without typing /prestige every time.
 * Non-stackable — intended as a permanent key item.
 */
public class PetCollectionBookItem extends Item {

    public PetCollectionBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (!com.arcadia.pets.PetsGlobalFlags.PETS_ENABLED && !sp.hasPermissions(2)) {
                sp.sendSystemMessage(Component.literal("§c[Arcadia] Pets are currently disabled on this server.")
                        .withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            if (player.isShiftKeyDown()) {
                // Shift + right-click → open the pet panel for the active/designated pet
                java.util.UUID designated = PetManager.getDesignatedPetId(sp.getUUID());
                if (designated != null) {
                    net.minecraft.world.item.ItemStack petStack = PetManager.findPetStackAnywhere(sp, designated);
                    if (!petStack.isEmpty()) {
                        PetManager.openPanelFor(sp, petStack);
                        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
                    }
                }
                sp.sendSystemMessage(Component.translatable("arcadia_prestige.msg.no_active_pet")
                        .withStyle(ChatFormatting.RED));
            } else {
                // Right-click → open the dashboard Pets tab (/pets)
                com.arcadia.pets.server.DashboardMenuBridge.openPetsTab(sp);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("arcadia_prestige.item.pet_collection_book.desc")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("arcadia_prestige.item.pet_collection_book.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("arcadia_prestige.item.pet_collection_book.shift_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
