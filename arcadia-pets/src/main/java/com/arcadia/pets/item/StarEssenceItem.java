package com.arcadia.pets.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Consumable item that upgrades a random stat on a pet by +1.
 * Can only be applied once per pet, through the Dashboard GUI.
 */
public class StarEssenceItem extends Item {

    public StarEssenceItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Star Essence is used via the Dashboard GUI, not direct right-click
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("arcadia_pets.item.star_essence.desc1").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipComponents.add(Component.translatable("arcadia_pets.item.star_essence.desc2").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("arcadia_pets.item.star_essence.desc3").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /**
     * Applies star essence to a pet, upgrading a random stat below 5 by +1.
     *
     * @param petStack     the pet ItemStack to upgrade
     * @param essenceStack the star essence ItemStack to consume
     * @return true if the upgrade was applied, false if the pet is null, already modified, or all stats are maxed
     */
    public static boolean applyToPet(ItemStack petStack, ItemStack essenceStack) {
        PetData data = PetData.fromStack(petStack);
        if (data == null || data.modifierApplied()) {
            return false;
        }

        // Find all stats below max (5)
        List<PetStat> upgradeable = new ArrayList<>();
        for (Map.Entry<PetStat, Integer> entry : data.stats().entrySet()) {
            if (entry.getValue() < 5) {
                upgradeable.add(entry.getKey());
            }
        }

        if (upgradeable.isEmpty()) {
            return false;
        }

        // Pick a random stat to upgrade
        PetStat chosen = upgradeable.get(ThreadLocalRandom.current().nextInt(upgradeable.size()));
        int newValue = data.stats().get(chosen) + 1;

        // Create new stats map with the upgraded stat
        var newStats = new java.util.EnumMap<>(data.stats());
        newStats.put(chosen, newValue);

        // Create new PetData with modifierApplied = true
        PetData upgraded = new PetData(
                data.petId(),
                data.mobType(),
                data.rarity(),
                newStats,
                true,
                data.customName(),
                data.hunger(),
                data.happiness(),
                data.skills()
        );
        upgraded.applyToStack(petStack);

        // Consume one essence
        essenceStack.shrink(1);

        return true;
    }
}
