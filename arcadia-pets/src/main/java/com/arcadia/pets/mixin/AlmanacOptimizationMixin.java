package com.arcadia.pets.mixin;

import com.arcadia.pets.item.PetItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents Almanac's per-tick NBT deep-copy scan from running on PetItems.
 * Almanac hooks into PlayerTickEvent.Post and calls checkAndFix() on every
 * inventory slot, which does a full CompoundTag.copy() for items with
 * CustomData. PetItems have large NBT, so this was the dominant tick cost.
 *
 * checkAndFix() returns boolean (was anything modified). Returning false means
 * "nothing to fix" — correct for our items since we never store empty CustomData.
 */
@Mixin(targets = "com.frikinjay.almanac.util.ItemNBTUtil", remap = false)
public class AlmanacOptimizationMixin {

    @Inject(method = "checkAndFix", at = @At("HEAD"), cancellable = true, remap = false)
    private static void skipForPetItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!stack.isEmpty() && stack.getItem() instanceof PetItem) {
            cir.setReturnValue(false);
        }
    }
}
