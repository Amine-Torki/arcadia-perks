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
 * A premium pet snack. Right-clicking gives +50 skill XP, +50 hunger, +5 HP to the active pet.
 */
public class PetSnackItem extends Item {

    public static final int HUNGER_BONUS    = 50;

    public PetSnackItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (PetManager.getActivePetData(sp.getUUID()) == null
                    && PetManager.getPocketPetData(sp.getUUID()) == null
                    && PetManager.getDesignatedPetId(sp.getUUID()) == null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .translatable("arcadia_pets.msg.no_active_pet")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }
            int count = player.isShiftKeyDown() ? stack.getCount() : 1;
            PetManager.addSkillXp(sp, 50 * count);
            PetManager.feedActivePet(sp, HUNGER_BONUS * count, 0, 5 * count);
            if (PetManager.getActivePetData(sp.getUUID()) == null && PetManager.getPocketPetData(sp.getUUID()) == null) {
                PetManager.reduceDeathCooldown(sp.getUUID(), 60_000L * count);
            }
            stack.shrink(count);
            return InteractionResultHolder.consume(stack);
        }
        if (level.isClientSide) {
            player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.6f);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("\uD83C\uDF56 +50  \u2764 +5 HP  \u2B50 +50 XP  [Shift] all")
                .withStyle(ChatFormatting.GREEN));
    }
}
