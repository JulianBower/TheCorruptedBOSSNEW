package com.hellgauntlet.boss.client;

import com.hellgauntlet.boss.TheCorruptedBossEntity;
import com.hellgauntlet.boss.TheCorruptedMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Renders The Corrupted: plain steel knight below 3 orb hits, charred obsidian with glowing veins from the third hit
 * on (emissive layer at full bright), the Gauntlet of Hell on the right arm, and the charging Void Orb with red
 * lightning above the raised gauntlet.
 */
public class TheCorruptedRenderer extends MobRenderer<TheCorruptedBossEntity, TheCorruptedModel> {
    private static final float MODEL_SCALE = 4.6F; // blockbench pixels -> a 3.4 block tall boss

    private static final ResourceLocation NORMAL = tex("the_corrupted_normal");
    private static final ResourceLocation AWAKENED = tex("the_corrupted_awakened");
    private static final ResourceLocation GLOW = tex("the_corrupted_glow");
    private static final ResourceLocation GAUNTLET = tex("gauntlet");
    private static final ResourceLocation GAUNTLET_GLOW = tex("gauntlet_glow");
    private static final ResourceLocation VOID_SPHERE = tex("void_sphere");
    private static final ResourceLocation BOLT =
            ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "textures/effect/corruption_bolt.png");

    public TheCorruptedRenderer(EntityRendererProvider.Context context) {
        super(context, new TheCorruptedModel(), 1.1F);
        this.addLayer(new GauntletLayer(this));
        this.addLayer(new GlowLayer(this));
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(TheCorruptedBossEntity boss) {
        return boss.getOrbHits() >= TheCorruptedBossEntity.PHASE_TWO_HITS ? AWAKENED : NORMAL;
    }

    @Override
    protected void scale(TheCorruptedBossEntity boss, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public boolean shouldRender(TheCorruptedBossEntity boss, Frustum frustum, double x, double y, double z) {
        return boss.isCharging() || super.shouldRender(boss, frustum, x, y, z);
    }

    @Override
    public void render(TheCorruptedBossEntity boss, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        super.render(boss, yaw, partialTick, poseStack, buffer, light);
        if (boss.isPhaseTwo() && !boss.isDeadOrDying()) {
            // phase 2: the boss carries the Corruption lightning himself
            BossVoidFx.bodyBolts(buffer.getBuffer(RenderType.entityTranslucentEmissive(BOLT)), poseStack.last(),
                    1.9F, boss.getBbHeight(), boss.getId(), boss.tickCount, 2);
        }
        if (boss.isCharging() && !boss.isDeadOrDying()) {
            renderOrb(boss, partialTick, poseStack, buffer);
        }
    }

    private void renderOrb(TheCorruptedBossEntity boss, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
        float progress = boss.getChargeProgress();
        float age = boss.tickCount + partialTick;
        float radius = (0.3F + 0.7F * progress) * (1.0F + 0.04F * Mth.sin(age * 0.9F));
        Vec3 offset = boss.orbPosition().subtract(boss.position());

        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);

        // core
        VertexConsumer core = buffer.getBuffer(RenderType.entityTranslucent(VOID_SPHERE));
        BossVoidFx.sphere(core, poseStack.last(), radius, 14, 20, 255);

        VertexConsumer additive = buffer.getBuffer(RenderType.lightning());

        // glow ring hugging the outline from any camera angle
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        BossVoidFx.haloRing(additive, poseStack.last(), radius * 0.97F, radius * (1.5F + 0.5F * progress), 150);
        poseStack.popPose();

        // red lightning over the orb, more of it the further the charge is
        BossVoidFx.sphereArcs(additive, poseStack.last(), radius, 4 + (int) (progress * 8.0F), boss.getId(),
                boss.tickCount, radius * 0.07F + 0.01F, 255);

        // tether down to the raised gauntlet
        BossVoidFx.tether(additive, poseStack.last(), new Vec3(0.0, -radius, 0.0),
                new Vec3(0.0, -(float) (TheCorruptedBossEntity.ORB_HEIGHT_OFFSET - 0.2) - 0.1, 0.0),
                boss.getId(), boss.tickCount, 0.03F, 255);
        poseStack.popPose();
    }

    /** The Gauntlet of Hell on the right arm. */
    private static class GauntletLayer extends RenderLayer<TheCorruptedBossEntity, TheCorruptedModel> {
        GauntletLayer(RenderLayerParent<TheCorruptedBossEntity, TheCorruptedModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int light, TheCorruptedBossEntity boss,
                           float limbSwing, float limbSwingAmount, float partialTick, float age, float netHeadYaw,
                           float headPitch) {
            if (boss.isInvisible()) {
                return;
            }
            this.getParentModel().renderGauntlet(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(GAUNTLET)),
                    light, LivingEntityRenderer.getOverlayCoords(boss, 0.0F), -1);
        }
    }

    /** Emissive layer (full bright): glowing veins, T-visor and cape edges once the boss is awakened. */
    private static class GlowLayer extends RenderLayer<TheCorruptedBossEntity, TheCorruptedModel> {
        GlowLayer(RenderLayerParent<TheCorruptedBossEntity, TheCorruptedModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int light, TheCorruptedBossEntity boss,
                           float limbSwing, float limbSwingAmount, float partialTick, float age, float netHeadYaw,
                           float headPitch) {
            if (boss.isInvisible() || boss.getOrbHits() < TheCorruptedBossEntity.PHASE_TWO_HITS) {
                return;
            }
            TheCorruptedModel model = this.getParentModel();
            model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityTranslucentEmissive(GLOW)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
            model.renderGauntlet(poseStack, buffer.getBuffer(RenderType.entityTranslucentEmissive(GAUNTLET_GLOW)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        }
    }
}
