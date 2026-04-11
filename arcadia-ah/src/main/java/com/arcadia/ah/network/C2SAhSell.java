package com.arcadia.ah.network;

import com.arcadia.ah.auction.AuctionManager;
import com.arcadia.lib.ArcadiaMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: player confirms selling an item at a specific price.
 * The item is taken from the player's inventory slot stored server-side.
 */
public record C2SAhSell(long price, int slotIndex) implements CustomPacketPayload {

    public static final Type<C2SAhSell> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_ah", "ah_sell"));

    public static final StreamCodec<FriendlyByteBuf, C2SAhSell> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> { buf.writeLong(pkt.price()); buf.writeInt(pkt.slotIndex()); },
                    buf -> new C2SAhSell(buf.readLong(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (price <= 0) {
                sp.sendSystemMessage(ArcadiaMessages.error("Price must be greater than 0."));
                return;
            }
            if (slotIndex < 0 || slotIndex >= sp.getInventory().getContainerSize()) return;

            ItemStack held = sp.getInventory().getItem(slotIndex);
            if (held.isEmpty()) {
                sp.sendSystemMessage(ArcadiaMessages.error("Item no longer in that slot."));
                return;
            }

            ItemStack toSell = held.copy();
            if (AuctionManager.listItem(sp, toSell, price, sp.getServer())) {
                // Remove item from inventory
                sp.getInventory().setItem(slotIndex, ItemStack.EMPTY);
                // Reopen the AH tab
                com.arcadia.lib.ArcadiaModRegistry.openTab(sp, 3);
            }
        });
    }
}
