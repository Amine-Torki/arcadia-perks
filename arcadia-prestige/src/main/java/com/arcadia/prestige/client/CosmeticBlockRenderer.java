package com.arcadia.prestige.client;

import com.arcadia.prestige.ArcadiaDashboard;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Renders small block companions for certain player cosmetic effects.
 *
 * <p>Each entry in {@link #BLOCK_COMPANIONS} maps a cosmetic ID to the block that
 * orbits the player. The orbit path exactly mirrors the particle head in
 * {@link ParticleRenderer} so the block rides on top of the comet/meteor trail.</p>
 *
 * <p>Add new entries to {@link #BLOCK_COMPANIONS} to give other cosmetics a block companion.</p>
 */
@EventBusSubscriber(modid = ArcadiaDashboard.MOD_ID, value = Dist.CLIENT)
public final class CosmeticBlockRenderer {

    /** Cosmetic ID → block that orbits the player for that effect. */
    private static final Map<String, BlockState> BLOCK_COMPANIONS = Map.of(
            "comet",  Blocks.MAGMA_BLOCK.defaultBlockState(),
            "meteor", Blocks.NETHERRACK.defaultBlockState()
    );

    /** Visual scale of the mini block (relative to a full block). */
    private static final float BLOCK_SCALE = 0.22f;

    /** Rotation speed in degrees per game tick. */
    private static final float SPIN_SPEED = 3.5f;

    private CosmeticBlockRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Map<UUID, String> allEffects = PlayerEffectCache.getAll();
        if (allEffects.isEmpty()) return;

        ClientLevel level      = mc.level;
        Vec3        cam        = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack   poseStack  = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        boolean firstPerson = mc.options.getCameraType() == CameraType.FIRST_PERSON;
        boolean hideOwn     = PlayerEffectCache.isHideOwnEffectsFirstPerson();
        UUID    localUuid   = mc.player.getUUID();

        for (Map.Entry<UUID, String> entry : allEffects.entrySet()) {
            BlockState bs = BLOCK_COMPANIONS.get(entry.getValue().toLowerCase());
            if (bs == null) continue;

            boolean isLocal = entry.getKey().equals(localUuid);
            if (isLocal && hideOwn && firstPerson) continue;

            Player target = level.getPlayerByUUID(entry.getKey());
            if (target == null) continue;
            if (target.distanceToSqr(mc.player) > 900.0) continue;

            // Orbit position — same formula as ParticleRenderer.renderComet / renderMeteor
            // so the block rides exactly at the particle head position.
            double gameTime = level.getGameTime() + partialTick;
            double angle    = gameTime * 0.09;   // matches tick * 0.09 in ParticleRenderer
            double rx = 1.45, rz = 0.85;

            double wx = target.getX() + Math.cos(angle) * rx;
            double wy = target.getY() + 0.9 + Math.sin(angle * 0.7) * 0.30;
            double wz = target.getZ() + Math.sin(angle) * rz;

            // Camera-relative coords
            double crx = wx - cam.x;
            double cry = wy - cam.y;
            double crz = wz - cam.z;

            // Sample light at the block's world position
            BlockPos lightPos    = BlockPos.containing(wx, wy, wz);
            int      packedLight = LightTexture.pack(
                    level.getBrightness(LightLayer.BLOCK, lightPos),
                    level.getBrightness(LightLayer.SKY,   lightPos));

            float spin = (float) (gameTime * SPIN_SPEED % 360.0);

            poseStack.pushPose();
            poseStack.translate(crx, cry, crz);
            poseStack.scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(25f)); // slight tilt
            poseStack.translate(-0.5, -0.5, -0.5);                           // centre block origin

            mc.getBlockRenderer().renderSingleBlock(
                    bs, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);

            poseStack.popPose();
        }

        bufferSource.endBatch();
    }
}
