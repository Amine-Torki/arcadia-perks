package com.arcadia.pets.client;

import com.arcadia.pets.ArcadiaPets;
import com.arcadia.pets.network.S2CPocketPet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders pocket-mode pets entirely on the client.
 *
 * <p>No server entity is ever spawned for a pocket pet. The server sends an
 * {@link S2CPocketPet} packet with the mob type and scale; this class creates
 * a <em>local</em> {@link LivingEntity} (not added to any level) and draws it
 * manually via {@link RenderLevelStageEvent.Stage#AFTER_ENTITIES} at the owner's
 * shoulder position — at full frame rate with a sinusoidal bob.</p>
 *
 * <p>When the owner leaves range or the pet is recalled, the server sends a
 * recall signal ({@code mobType == ""}) and the fake entity is discarded.</p>
 */
@EventBusSubscriber(modid = ArcadiaPets.MOD_ID, value = Dist.CLIENT)
public final class PocketPetRenderer {

    /** ownerUuid → fake locally-created entity (not in any world). */
    private static final Map<UUID, LivingEntity> fakePets = new ConcurrentHashMap<>();

    private PocketPetRenderer() {}

    // ── Packet handler ────────────────────────────────────────────────────────

    public static void onPacket(S2CPocketPet pkt) {
        UUID ownerUuid = pkt.ownerUuid();

        // Remove any stale entry for this owner
        LivingEntity old = fakePets.remove(ownerUuid);
        if (old != null) old.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

        // Empty mobType = recall signal → just clear
        if (pkt.mobType().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        // Create a local-only entity (not added to any level)
        ResourceLocation typeRL = ResourceLocation.parse(pkt.mobType());
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeRL);
        if (type == null) return;

        net.minecraft.world.entity.Entity raw = type.create(level);
        if (!(raw instanceof LivingEntity living)) return;

        // Apply scale attribute
        AttributeInstance scaleAttr = living.getAttribute(Attributes.SCALE);
        if (scaleAttr != null) scaleAttr.setBaseValue(pkt.scale());

        if (pkt.customName() != null && !pkt.customName().isEmpty()) {
            living.setCustomName(Component.literal(pkt.customName()).withStyle(ChatFormatting.YELLOW));
            living.setCustomNameVisible(true);
        }

        // Suppress AI on the fake entity so head-tracking/movement don't animate
        if (living instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);

        fakePets.put(ownerUuid, living);
    }

    // ── Render via RenderLevelStageEvent ──────────────────────────────────────

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (fakePets.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        EntityRenderDispatcher erd = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();

        boolean firstPerson = mc.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON;
        boolean hideOwn    = PetClientSettings.isHideOwnEffectsFirstPerson();
        UUID localUuid     = mc.player != null ? mc.player.getUUID() : null;

        for (var entry : fakePets.entrySet()) {
            UUID ownerUuid  = entry.getKey();
            LivingEntity fake = entry.getValue();

            // Hide own pocket pet in first person when toggle is on
            if (hideOwn && firstPerson && ownerUuid.equals(localUuid)) continue;

            Player owner = level.getPlayerByUUID(ownerUuid);
            if (owner == null) continue;

            // Interpolate owner position and yaw so the pet tracks at full frame rate
            // instead of snapping every game tick.
            double ix = Mth.lerp(partialTick, owner.xOld, owner.getX());
            double iy = Mth.lerp(partialTick, owner.yOld, owner.getY());
            double iz = Mth.lerp(partialTick, owner.zOld, owner.getZ());
            float  iYaw = Mth.lerp(partialTick, owner.yRotO, owner.getYRot());

            // Shoulder offset — right of where the owner faces, at head height
            float yawRad = (float) Math.toRadians(iYaw);
            double rightX = Math.cos(yawRad) * 0.65;
            double rightZ = Math.sin(yawRad) * 0.65;
            double bob    = Math.sin((owner.tickCount + partialTick) * 0.18) * 0.07;

            // World-space shoulder position
            double wx = ix + rightX;
            double wy = iy + 1.3 + bob;
            double wz = iz + rightZ;

            // Camera-relative render coordinates
            double rx = wx - cam.x;
            double ry = wy - cam.y;
            double rz = wz - cam.z;

            // Sample packed light from owner's block position
            BlockPos lightPos = BlockPos.containing(wx, wy, wz);
            int packedLight = LightTexture.pack(
                    level.getBrightness(LightLayer.BLOCK, lightPos),
                    level.getBrightness(LightLayer.SKY,   lightPos));

            // Sync world position so name-tag distance checks and bounding-box
            // positioning use the correct location, not the default 0,0,0.
            fake.setPos(wx, wy, wz);

            // Orient pet to face the same direction as the player.
            // Must set all four rotation fields: entity yaw, body yaw, and head yaw
            // (plus their "previous tick" counterparts used for interpolation).
            fake.setYRot(iYaw);
            fake.yRotO       = iYaw;
            fake.yBodyRot    = iYaw;
            fake.yBodyRotO   = iYaw;
            fake.setYHeadRot(iYaw);
            fake.yHeadRotO   = iYaw;

            // Draw the fake entity
            poseStack.pushPose();
            // EnderDragon's renderer ignores Attributes.SCALE and standard yRot fields —
            // apply manual model scale and rotation via the pose stack.
            if (fake.getType() == EntityType.ENDER_DRAGON) {
                poseStack.translate(rx, ry, rz);
                float dragonScale = 0.06f;
                poseStack.scale(dragonScale, dragonScale, dragonScale);
                // Rotate to face the player's look direction (dragon model faces +Z by default)
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-iYaw + 180));
                erd.render(fake, 0, 0, 0, iYaw, partialTick, poseStack, bufferSource, packedLight);
            } else {
                erd.render(fake, rx, ry, rz, iYaw, partialTick, poseStack, bufferSource, packedLight);
            }
            poseStack.popPose();

            // Draw name tag manually — bypasses EntityRenderer.shouldShowName() checks
            // that can silently fail for off-level fake entities.
            Component customName = fake.getCustomName();
            if (customName != null) {
                AttributeInstance scaleAttr = fake.getAttribute(Attributes.SCALE);
                float entityScale = scaleAttr != null ? (float) scaleAttr.getValue() : 1.0f;
                double nameY = ry + fake.getBbHeight() * entityScale + 0.25;

                poseStack.pushPose();
                poseStack.translate(rx, nameY, rz);
                poseStack.mulPose(mc.gameRenderer.getMainCamera().rotation());
                poseStack.scale(-0.025f, -0.025f, 0.025f);

                Font font = mc.font;
                float textX = -font.width(customName) / 2.0f;
                font.drawInBatch(customName, textX, 0f, 0xFFFFFF, false,
                        poseStack.last().pose(), bufferSource,
                        Font.DisplayMode.SEE_THROUGH, 0x40000000, packedLight);
                poseStack.popPose();
            }
        }

        // Flush render batches
        bufferSource.endBatch();
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public static void clear() {
        fakePets.values().forEach(e ->
                e.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED));
        fakePets.clear();
    }
}
