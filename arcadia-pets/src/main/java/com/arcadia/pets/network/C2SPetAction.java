package com.arcadia.pets.network;

import com.arcadia.pets.server.PetManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record C2SPetAction(int actionId, UUID petId) implements CustomPacketPayload {

    public static final int SUMMON_RECALL = 0;
    public static final int FEED         = 1;
    /** actionId = SET_MOVEMENT * 256 + PetMovementMode.ordinal() */
    public static final int SET_MOVEMENT  = 2;
    /** actionId = SET_BEHAVIOUR * 256 + PetBehaviourMode.ordinal() */
    public static final int SET_BEHAVIOUR = 3;
    /** Opens the pet panel for the player's currently active pet. petId is ignored. */
    public static final int OPEN_PANEL   = 4;
    /** actionId = TOGGLE_SKILL * 256 + skill slot index */
    public static final int TOGGLE_SKILL = 5;

    public static final Type<C2SPetAction> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pet_action"));

    public static final StreamCodec<FriendlyByteBuf, C2SPetAction> STREAM_CODEC =
            StreamCodec.of(C2SPetAction::encode, C2SPetAction::decode);

    private static void encode(FriendlyByteBuf buf, C2SPetAction pkt) {
        buf.writeInt(pkt.actionId);
        buf.writeUUID(pkt.petId);
    }

    private static C2SPetAction decode(FriendlyByteBuf buf) {
        return new C2SPetAction(buf.readInt(), buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                PetManager.handlePetAction(sp, actionId, petId);
            }
        });
    }
}
