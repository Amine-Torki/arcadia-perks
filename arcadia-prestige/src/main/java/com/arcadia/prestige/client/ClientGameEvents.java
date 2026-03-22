package com.arcadia.prestige.client;

import com.arcadia.prestige.ArcadiaDashboard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client-side NeoForge game-bus event handlers.
 */
@EventBusSubscriber(modid = ArcadiaDashboard.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientGameEvents {

    private ClientGameEvents() {}

    /** Injects a live-computed "Expires in Xh Ym" line for AH listing items. */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        CustomData data = event.getItemStack().get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        if (!tag.contains("arcadia_ah_expires")) return;

        long remainMs = tag.getLong("arcadia_ah_expires") - System.currentTimeMillis();
        Component line;
        if (remainMs <= 0) {
            line = Component.literal("§cExpired");
        } else {
            long hours = remainMs / 3_600_000L;
            long mins  = (remainMs % 3_600_000L) / 60_000L;
            line = Component.literal("§7Expires in §f" + hours + "h " + mins + "m");
        }
        event.getToolTip().add(line);
    }
}
