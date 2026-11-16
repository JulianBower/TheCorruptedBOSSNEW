package com.hellgauntlet.boss.client;

import com.hellgauntlet.boss.ClientFxState;
import com.hellgauntlet.boss.ModEffects;
import com.hellgauntlet.boss.TheCorruptedMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Jagged red lightning that snaps around every entity (players and mobs) that has the Corruption effect. */
@EventBusSubscriber(modid = TheCorruptedMod.MODID, value = Dist.CLIENT)
public final class CorruptionLightningRenderer {
    private static final ResourceLocation BOLT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "textures/effect/corruption_bolt.png");

    private CorruptionLightningRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(BOLT_TEXTURE);
        VertexConsumer consumer = null;

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living.isRemoved()) {
                continue;
            }
            MobEffectInstance effect = living.getEffect(ModEffects.CORRUPTION);
            // mobs do not get their effects synced by vanilla: ClientFxState is filled by CorruptionSyncPayload
            if (effect == null && !ClientFxState.isCorrupted(living.getId(), level.getGameTime())) {
                continue;
            }
            if (entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
                continue;
            }
            if (!event.getFrustum().isVisible(living.getBoundingBoxForCulling().inflate(1.5))) {
                continue;
            }
            if (consumer == null) {
                consumer = buffers.getBuffer(type);
            }
            double x = Mth.lerp(partial, living.xOld, living.getX()) - cam.x;
            double y = Mth.lerp(partial, living.yOld, living.getY()) - cam.y;
            double z = Mth.lerp(partial, living.zOld, living.getZ()) - cam.z;
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            BossVoidFx.bodyBolts(consumer, poseStack.last(), living.getBbWidth(), living.getBbHeight(),
                    living.getId(), living.tickCount, effect == null ? 0 : effect.getAmplifier());
            poseStack.popPose();
        }
        if (consumer != null) {
            buffers.endBatch(type);
        }
    }
}
