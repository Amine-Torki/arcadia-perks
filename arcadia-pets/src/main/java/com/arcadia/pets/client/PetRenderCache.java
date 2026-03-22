package com.arcadia.pets.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.*;

/**
 * Renders each pet mob type once to an off-screen RenderTarget and reuses that
 * texture for every subsequent GUI slot draw.  Non-GUI contexts (hand, ground,
 * frames) still go through the live EntityRenderDispatcher path.
 *
 * Flow:
 *   1. renderByItem (GUI) — cache miss → PetRenderCache.request(mobType), fall through
 *   2. RenderFrameEvent.Pre → PetRenderCache.flushPending() renders to RTT + registers texture
 *   3. renderByItem (GUI) from the next frame onwards — cache hit → blit (zero entity render cost)
 */
@OnlyIn(Dist.CLIENT)
public final class PetRenderCache {

    /**
     * Size of the off-screen render target in pixels.
     * 128 is chosen to cover both the inventory slot icon (~16px on-screen)
     * and the HUD portrait (36×36 GUI px × up to ~3× GUI scale = ~108 actual px).
     */
    private static final int RT_SIZE = 128;

    private static final Map<String, RenderTarget>      CACHE    = new HashMap<>();
    private static final Map<String, ResourceLocation>  TEX_LOCS = new HashMap<>();
    private static final Set<String>                    PENDING  = new LinkedHashSet<>();

    private PetRenderCache() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns the cached texture location for GUI blitting, or null on cache miss. */
    public static ResourceLocation get(String mobType) {
        return TEX_LOCS.get(mobType);
    }

    /** Schedules a mob type for RTT rendering on the next pre-frame hook. */
    public static void request(String mobType) {
        if (!CACHE.containsKey(mobType)) PENDING.add(mobType);
    }

    /**
     * Called from RenderFrameEvent.Pre.
     * Renders all pending mob types to their own RenderTargets and registers the
     * resulting textures with the TextureManager so RenderType.text() can sample them.
     */
    public static void flushPending() {
        if (PENDING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.cameraEntity == null) return;

        // Save current GL projection so we can restore it after the RTT renders
        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        int winW = mc.getWindow().getWidth();
        int winH = mc.getWindow().getHeight();

        for (String mobType : new ArrayList<>(PENDING)) {
            LivingEntity entity = ClientPetCache.getEntity(mobType);
            if (entity == null) continue;

            TextureTarget rt = new TextureTarget(RT_SIZE, RT_SIZE, true, Minecraft.ON_OSX);
            rt.setClearColor(0f, 0f, 0f, 0f);
            rt.clear(Minecraft.ON_OSX);
            rt.bindWrite(true);
            RenderSystem.viewport(0, 0, RT_SIZE, RT_SIZE);

            // Orthographic projection covering the [0,1]×[0,1] unit square used in renderByItem
            RenderSystem.setProjectionMatrix(
                new Matrix4f().ortho(0f, 1f, 0f, 1f, -100f, 100f),
                VertexSorting.ORTHOGRAPHIC_Z
            );

            // Apply exactly the same GUI transforms as PetItemRenderer (live render path)
            PoseStack ps = new PoseStack();
            ps.translate(0.5, 0.0, 0.5);

            float mobH   = entity.getBbHeight();
            float mobW   = entity.getBbWidth();
            float maxDim = Math.max(mobH * 0.85f, mobW * 1.2f);
            // Floor prevents small mobs (chicken, allay, wolf) from being over-scaled
            maxDim = Math.max(maxDim, 1.0f);
            float scale = (0.9f / maxDim) * 0.625f;

            boolean isDragon = "minecraft:ender_dragon".equals(mobType);
            if (isDragon) {
                ps.translate(0.12, 0.08, -0.12);
                scale *= 3.6f;
                ps.mulPose(new Quaternionf().rotateY((float) Math.toRadians(45)));
            } else {
                ps.translate(0, 0.17, 0);
                ps.mulPose(new Quaternionf().rotateY((float) Math.toRadians(20)));
            }
            ps.scale(scale, scale, scale);
            ps.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-10)));

            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            mc.getEntityRenderDispatcher().setRenderShadow(false);
            // Fix camera orientation so entity head doesn't track game camera yaw
            mc.getEntityRenderDispatcher().overrideCameraOrientation(new Quaternionf());
            // packedLight = LightTexture.FULL_BRIGHT (15728880) — bypass world lighting
            mc.getEntityRenderDispatcher().render(entity, 0, 0, 0, 0f, 0f, ps, buf, 15728880);
            buf.endBatch();
            mc.getEntityRenderDispatcher().setRenderShadow(true);

            // Restore main render target + viewport + projection
            mc.getMainRenderTarget().bindWrite(false);
            RenderSystem.viewport(0, 0, winW, winH);
            RenderSystem.setProjectionMatrix(savedProj, VertexSorting.DISTANCE_TO_ORIGIN);

            // Register RTT colour texture with TextureManager so RenderType.text() can sample it
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("arcadia_prestige",
                "pet_icon_" + mobType.replace(":", "_").replace("/", "_"));
            final int glTexId = rt.getColorTextureId();
            mc.getTextureManager().register(loc, new AbstractTexture() {
                @Override public void load(ResourceManager rm) { /* static GPU texture, nothing to load */ }
                @Override public int getId() { return glTexId; }
                @Override public void releaseId() { /* lifetime managed by RenderTarget.destroyBuffers() */ }
            });

            CACHE.put(mobType, rt);
            TEX_LOCS.put(mobType, loc);
        }
        PENDING.clear();
    }

    /**
     * Draws the cached texture as a 2D quad covering the full item slot (1×1 model space).
     * V coordinates are flipped so GL's bottom-origin maps correctly to GUI's top-origin.
     */
    public static void blit(ResourceLocation texLoc, PoseStack poseStack, MultiBufferSource buffer) {
        net.minecraft.client.renderer.RenderType renderType =
                net.minecraft.client.renderer.RenderType.text(texLoc);
        VertexConsumer vc = buffer.getBuffer(renderType);
        Matrix4f mat = poseStack.last().pose();
        int light = net.minecraft.client.renderer.LightTexture.pack(15, 15);
        vc.addVertex(mat, 0, 0, 0).setColor(255, 255, 255, 255).setUv(0f, 0f).setLight(light);
        vc.addVertex(mat, 1, 0, 0).setColor(255, 255, 255, 255).setUv(1f, 0f).setLight(light);
        vc.addVertex(mat, 1, 1, 0).setColor(255, 255, 255, 255).setUv(1f, 1f).setLight(light);
        vc.addVertex(mat, 0, 1, 0).setColor(255, 255, 255, 255).setUv(0f, 1f).setLight(light);
    }

    /**
     * Draws the cached RTT texture into a GUI at pixel coordinates.
     * Uses {@link GuiGraphics#bufferSource()} so the draw is flushed in the
     * correct GUI render pass without needing a manual endBatch call.
     * V coordinates are flipped to account for GL bottom-origin vs GUI top-origin.
     */
    public static void blitGui(ResourceLocation texLoc, GuiGraphics g, int x, int y, int w, int h) {
        net.minecraft.client.renderer.RenderType rt =
                net.minecraft.client.renderer.RenderType.text(texLoc);
        VertexConsumer vc = g.bufferSource().getBuffer(rt);
        Matrix4f mat = g.pose().last().pose();
        int light = net.minecraft.client.renderer.LightTexture.pack(15, 15);
        vc.addVertex(mat, x,     y,     0).setColor(255, 255, 255, 255).setUv(0f, 1f).setLight(light);
        vc.addVertex(mat, x + w, y,     0).setColor(255, 255, 255, 255).setUv(1f, 1f).setLight(light);
        vc.addVertex(mat, x + w, y + h, 0).setColor(255, 255, 255, 255).setUv(1f, 0f).setLight(light);
        vc.addVertex(mat, x,     y + h, 0).setColor(255, 255, 255, 255).setUv(0f, 0f).setLight(light);
        g.bufferSource().endBatch(rt);
    }

    /** Invalidate a single mob type (e.g. if cosmetic skins are added later). */
    public static void invalidate(String mobType) {
        RenderTarget rt = CACHE.remove(mobType);
        if (rt != null) rt.destroyBuffers();
        ResourceLocation loc = TEX_LOCS.remove(mobType);
        if (loc != null) Minecraft.getInstance().getTextureManager().release(loc);
        PENDING.add(mobType); // re-schedule for next frame
    }

    /** Release all GPU resources. Must be called on disconnect. */
    public static void clearAll() {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        CACHE.values().forEach(RenderTarget::destroyBuffers);
        TEX_LOCS.values().forEach(tm::release);
        CACHE.clear();
        TEX_LOCS.clear();
        PENDING.clear();
    }
}
