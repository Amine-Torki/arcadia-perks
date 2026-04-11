package com.arcadia.lib.network;

import com.arcadia.lib.ArcadiaLib;
import com.arcadia.lib.ArcadiaModRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network registration for Arcadia Lib — syncs staff role to client.
 *
 * @author vyrriox
 */
public final class ArcadiaLibNet {

    private ArcadiaLibNet() {}

    // ── Staff sync payload ──────────────────────────────────────────────────

    public record StaffSyncPayload(int roleLevel) implements CustomPacketPayload {
        public static final Type<StaffSyncPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ArcadiaLib.MOD_ID, "staff_sync"));

        public static final StreamCodec<FriendlyByteBuf, StaffSyncPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> buf.writeVarInt(payload.roleLevel),
                        buf -> new StaffSyncPayload(buf.readVarInt())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Registration ────────────────────────────────────────────────────────

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ArcadiaLib.MOD_ID).versioned("1.0");
        registrar.playToClient(
                StaffSyncPayload.TYPE,
                StaffSyncPayload.STREAM_CODEC,
                ArcadiaLibNet::handleStaffSync
        );
    }

    private static void handleStaffSync(StaffSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ArcadiaModRegistry.setClientStaffLevel(payload.roleLevel());
        });
    }

    // ── Server-side send ────────────────────────────────────────────────────

    public static void sendStaffSync(ServerPlayer player, int roleLevel) {
        PacketDistributor.sendToPlayer(player, new StaffSyncPayload(roleLevel));
    }
}
