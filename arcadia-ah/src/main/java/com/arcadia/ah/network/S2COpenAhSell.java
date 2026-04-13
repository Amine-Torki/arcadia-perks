package com.arcadia.ah.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: open the AH sell price-input screen.
 * Carries the item name and slot index for display and confirmation.
 */
public record S2COpenAhSell(String itemName, int itemCount, int slotIndex) implements CustomPacketPayload {

    public static final Type<S2COpenAhSell> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_ah", "open_ah_sell"));

    public static final StreamCodec<FriendlyByteBuf, S2COpenAhSell> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> { buf.writeUtf(pkt.itemName()); buf.writeInt(pkt.itemCount()); buf.writeInt(pkt.slotIndex()); },
                    buf -> new S2COpenAhSell(buf.readUtf(), buf.readInt(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // Build a display-only stack from the player's actual inventory on client side
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(slotIndex);
            com.arcadia.ah.client.AhSellScreen.open(stack, slotIndex);
        });
    }
}
