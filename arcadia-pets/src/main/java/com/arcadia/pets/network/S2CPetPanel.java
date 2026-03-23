package com.arcadia.pets.network;

import com.arcadia.pets.item.PetBehaviourMode;
import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetMovementMode;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2CPetPanel(CompoundTag petTag, int cooldownTicks, boolean petActive,
                          int movementOrdinal, int behaviourOrdinal,
                          float currentHp, float maxHp,
                          CompoundTag skillCooldowns,
                          CompoundTag skillToggles)
        implements CustomPacketPayload {

    public static final Type<S2CPetPanel> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("arcadia_pets", "pet_panel"));

    public static final StreamCodec<FriendlyByteBuf, S2CPetPanel> STREAM_CODEC =
            StreamCodec.of(S2CPetPanel::encode, S2CPetPanel::decode);

    private static void encode(FriendlyByteBuf buf, S2CPetPanel pkt) {
        buf.writeNbt(pkt.petTag);
        buf.writeInt(pkt.cooldownTicks);
        buf.writeBoolean(pkt.petActive);
        buf.writeInt(pkt.movementOrdinal);
        buf.writeInt(pkt.behaviourOrdinal);
        buf.writeFloat(pkt.currentHp);
        buf.writeFloat(pkt.maxHp);
        buf.writeNbt(pkt.skillCooldowns);
        buf.writeNbt(pkt.skillToggles);
    }

    private static S2CPetPanel decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        int cd = buf.readInt();
        boolean active = buf.readBoolean();
        int mov = buf.readInt();
        int beh = buf.readInt();
        float curHp = buf.readFloat();
        float maxHp = buf.readFloat();
        CompoundTag cds = buf.readNbt();
        if (cds == null) cds = new CompoundTag();
        CompoundTag toggles = buf.readNbt();
        if (toggles == null) toggles = new CompoundTag();
        return new S2CPetPanel(tag, cd, active, mov, beh, curHp, maxHp, cds, toggles);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            PetData data = PetData.fromTag(petTag);
            PetMovementMode  move  = PetMovementMode.values() [Math.min(movementOrdinal,  PetMovementMode.values().length  - 1)];
            PetBehaviourMode behav = PetBehaviourMode.values()[Math.min(behaviourOrdinal, PetBehaviourMode.values().length - 1)];
            // Keep HUD behaviour state in sync
            com.arcadia.pets.client.ClientPetState.updateBehaviour(behaviourOrdinal);
            // Convert remaining-ms to absolute end-times so the panel can tick down without server updates
            long now = System.currentTimeMillis();
            java.util.Map<String, Long> cdEnds = new java.util.HashMap<>();
            for (String key : skillCooldowns.getAllKeys()) {
                long rem = skillCooldowns.getLong(key);
                cdEnds.put(key, now + rem);
            }
            // Build skill toggle map (skillId → enabled)
            java.util.Map<String, Boolean> toggleMap = new java.util.HashMap<>();
            for (String key : skillToggles.getAllKeys()) {
                toggleMap.put(key, skillToggles.getBoolean(key));
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof com.arcadia.pets.client.PetScreen ps
                    && ps.getPetId().equals(data.petId())) {
                ps.updateData(data, cooldownTicks, petActive, move, behav, currentHp, maxHp, cdEnds, toggleMap);
            } else {
                mc.setScreen(new com.arcadia.pets.client.PetScreen(data, cooldownTicks, petActive, move, behav, currentHp, maxHp, cdEnds, toggleMap));
            }
        });
    }
}
