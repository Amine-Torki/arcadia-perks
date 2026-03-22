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
 * A pet treat. Right-clicking gives +10 skill XP, +30 hunger, +3 HP to the active pet.
 */
public class PetTreatItem extends Item {

    public PetTreatItem(Properties properties) {
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
                        .translatable("arcadia_prestige.msg.no_active_pet")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }
            int count = player.isShiftKeyDown() ? stack.getCount() : 1;
            PetManager.addSkillXp(sp, 10 * count);
            PetManager.feedActivePet(sp, 30 * count, 0, 3 * count);
            // Only reduce death cooldown if no pet is currently active
            // (avoids accidentally reviving a dead pet while feeding a different active pet)
            if (PetManager.getActivePetData(sp.getUUID()) == null && PetManager.getPocketPetData(sp.getUUID()) == null) {
                PetManager.reduceDeathCooldown(sp.getUUID(), 60_000L * count);
            }
            stack.shrink(count);
            return InteractionResultHolder.consume(stack);
        }
        // Client-side: play a subtle XP chime on right-click
        if (level.isClientSide) {
            player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.6f);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("\uD83C\uDF56 +30  \u2764 +3 HP  \u2B50 +10 XP  [Shift] all")
                .withStyle(ChatFormatting.GREEN));
    }
}
