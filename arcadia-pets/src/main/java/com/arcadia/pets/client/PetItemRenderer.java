package com.arcadia.pets.client;

import com.arcadia.pets.item.PetData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
public class PetItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static final PetItemRenderer INSTANCE = new PetItemRenderer();

    private PetItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // Fast path for GUI: check RTT cache using only a raw tag string read — no full deserialization
        if (displayContext == ItemDisplayContext.GUI) {
            CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd != null && cd.contains("PetId")) {
                String mobType = cd.getUnsafe().getString("MobType");
                ResourceLocation cached = PetRenderCache.get(mobType);
                if (cached != null) {
                    poseStack.pushPose();
                    PetRenderCache.blit(cached, poseStack, buffer);
                    poseStack.popPose();
                    return;
                }
                // Cache miss — schedule RTT and fall through to live render
                PetRenderCache.request(mobType);
            }
        }

        PetData data = PetData.fromStack(stack);
        if (data == null || data.mobType().isEmpty()) return;

        LivingEntity entity = ClientPetCache.getEntity(data.mobType());
        if (entity == null) return;

        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        poseStack.pushPose();

        // Standardize position to center of item slot (live render path only)
        poseStack.translate(0.5D, 0.0D, 0.5D);

        // Get mob's dimensions to scale it tightly
        float mobHeight = entity.getBbHeight();
        float mobWidth  = entity.getBbWidth();
        // We calculate a footprint scale. For very tall, thin mobs (Enderman, Parrot),
        // scaling strictly by height makes them look way too small.
        // We use Math.max(height, width * 1.5) to give width a bit more weight for wide mobs,
        // but let tall mobs be a bit taller in the slot.
        float maxDim = Math.max(mobHeight * 0.85f, mobWidth * 1.2f);
        if (maxDim == 0) maxDim = 1.0f;

        // Base scale keeps it neatly inside the 1x1x1 slot
        float autoScale = 0.9f / maxDim;

        // Force uniform size ignoring Endurance or mob type scale multipliers
        float finalGuiScale = autoScale;

        if (displayContext == ItemDisplayContext.GUI) {

            boolean isDragon = data.mobType().equals("minecraft:ender_dragon");

            // Size adjustment
            float adjustedGuiScale = finalGuiScale * 0.625f;
            
            if (isDragon) {
                // Focus on head/body: scale 3.6x (20% bigger than initial 3.0x)
                // Shifted slightly to keep the head/neck well-framed
                poseStack.translate(0.12, 0.08, -0.12); 
                adjustedGuiScale *= 3.6f;
                poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(45)));
            } else {
                poseStack.translate(0, 0.1, 0);
                poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(30)));
            }
            
            poseStack.scale(adjustedGuiScale, adjustedGuiScale, adjustedGuiScale);
            // Tilt slightly UPWARD instead of downward (-10 degrees)
            poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-10)));
        } 
        else if (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            boolean isDragon = data.mobType().equals("minecraft:ender_dragon");
            
            // Further boost dragon to fill the 'void' in hand
            float holdScale = autoScale * (isDragon ? 2.5f : 0.5f);
            
            if (isDragon) {
                // Focus on the head/buste area in hand - lowered head carefully
                poseStack.translate(0.12, 0.20, 0.25); 
            } else {
                poseStack.translate(0, 0.2, 0);
            }
            
            poseStack.scale(holdScale, holdScale, holdScale);
            
            if (isDragon) {
                poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(90)));
                poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(25))); // Slight angle for volume
            } else {
                poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(180)));
            }
        }
        else if (displayContext == ItemDisplayContext.GROUND) {
            // Dropped item spins naturally and shrinks
            poseStack.translate(0, 0.2, 0);
            float groundScale = autoScale * 0.5f;
            poseStack.scale(groundScale, groundScale, groundScale);
        }
        else {
            // Fixed frames, third person hands, head, etc.
            float holdScale = autoScale * 0.6f;
            poseStack.scale(holdScale, holdScale, holdScale);
            poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(180)));
        }

        // Draw the cached entity
        // Note: Render pure body, pass partialTick = 0, no shadows if possible.
        dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, poseStack, buffer, packedLight);

        poseStack.popPose();
    }
}
