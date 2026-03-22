package com.arcadia.ah.network;

import com.arcadia.ah.auction.AuctionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: player submitted an AH search query.
 * Server updates the player's search filter and sends fresh AH results back.
 */
public record C2SAhSearch(String query) implements CustomPacketPayload {

    public static final Type<C2SAhSearch> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_ah", "ah_search"));

    public static final StreamCodec<FriendlyByteBuf, C2SAhSearch> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeUtf(pkt.query()),
                    buf -> new C2SAhSearch(buf.readUtf(128))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            AuctionManager.setSearch(sp.getUUID(), query);
            // Send updated AH search results to client
            PacketDistributor.sendToPlayer(sp, new S2COpenAhSearch(query));
        });
    }
}
