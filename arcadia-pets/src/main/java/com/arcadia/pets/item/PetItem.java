package com.arcadia.pets.item;

import com.arcadia.pets.server.PetCollectionSavedData;
import com.arcadia.pets.server.PetManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * An item representing a collectible pet. Pet data is stored in the CUSTOM_DATA component.
 *
 * <p>Right-clicking auto-deposits the pet into the player's collection so it can only be
 * managed via /pets (the dashboard Pets tab). This prevents the exploit where a player
 * summons a pet and then drops or trades the item while keeping the entity active.</p>
 */
public class PetItem extends Item {

    public PetItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            // Despawn and deselect pet if it's active or designated for this player
            PetData data = PetData.fromStack(stack);
            if (data != null) {
                if (PetManager.isActivePet(sp.getUUID(), data.petId())) {
                    PetManager.despawn(sp);
                }
                PetManager.clearDesignatedPet(sp.getUUID(), data.petId());
            }
            boolean deposited = PetCollectionSavedData
                    .getOrCreate(sp.getServer())
                    .deposit(sp.getUUID(), stack);
            if (deposited) {
                stack.shrink(1);
                sp.sendSystemMessage(Component.translatable("arcadia_pets.msg.pet_deposited")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                sp.sendSystemMessage(Component.translatable("arcadia_pets.msg.collection_full")
                        .withStyle(ChatFormatting.RED));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        PetData data = PetData.fromStack(stack);
        if (data != null) {
            tooltipComponents.addAll(data.buildTooltip());
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || !cd.contains("PetId")) return super.getName(stack);
        CompoundTag tag = cd.getUnsafe();

        String mobName = tag.getString("MobType");
        if (mobName.contains(":")) mobName = mobName.substring(mobName.indexOf(':') + 1);
        mobName = mobName.substring(0, 1).toUpperCase() + mobName.substring(1).replace('_', ' ');

        CompoundTag statsTag = tag.getCompound("Stats");
        int totalStars = 0;
        for (PetStat stat : PetStat.values()) totalStars += statsTag.getInt(stat.name());
        int avgStars = Math.max(1, Math.round((float) totalStars / Math.max(1, PetStat.values().length)));
        String starPrefix = "[" + "\u2605".repeat(avgStars) + "]";

        String customName = tag.getString("CustomName");
        String displayName = !customName.isEmpty() ? starPrefix + " " + customName : starPrefix + " " + mobName;

        return Component.literal(displayName).withStyle(PetRarity.fromId(tag.getInt("Rarity")).getColor());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || !cd.contains("PetId")) return false;
        return PetRarity.fromId(cd.getUnsafe().getInt("Rarity")).hasGlint();
    }
}
