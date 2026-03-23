package com.arcadia.pets.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client: keep the local player's pet HP bar in sync.
 * Sent on summon, on each HP change, and on death (currentHp = 0).
 * {@code active = false} tells the client to hide the HUD bar entirely.
 */
public record S2CPetHpSync(float currentHp, float maxHp, boolean active, String petName, String mobType, int hunger)
        implements CustomPacketPayload {

    public static final Type<S2CPetHpSync> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pet_hp_sync"));

    public static final StreamCodec<FriendlyByteBuf, S2CPetHpSync> STREAM_CODEC =
            StreamCodec.of(S2CPetHpSync::encode, S2CPetHpSync::decode);

    private static void encode(FriendlyByteBuf buf, S2CPetHpSync pkt) {
        buf.writeFloat(pkt.currentHp);
        buf.writeFloat(pkt.maxHp);
        buf.writeBoolean(pkt.active);
        buf.writeUtf(pkt.petName, 20);
        buf.writeUtf(pkt.mobType, 64);
        buf.writeByte(Math.max(0, Math.min(100, pkt.hunger)));
    }

    private static S2CPetHpSync decode(FriendlyByteBuf buf) {
        float cur    = buf.readFloat();
        float max    = buf.readFloat();
        boolean act  = buf.readBoolean();
        String name  = buf.readUtf(20);
        String mob   = buf.readUtf(64);
        int hunger   = Byte.toUnsignedInt(buf.readByte());
        return new S2CPetHpSync(cur, max, act, name, mob, hunger);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            int event = com.arcadia.pets.client.ClientPetState.updateAndGetEvent(
                    currentHp, maxHp, active, petName, mobType, hunger);
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            switch (event) {
                case 1 -> // summoned
                    mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.45f, 1.7f, false);
                case 2 -> // recalled
                    mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f, false);
                case 3 -> // died
                    mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            net.minecraft.sounds.SoundEvents.TOTEM_USE,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 0.55f, false);
            }
        });
    }
}
