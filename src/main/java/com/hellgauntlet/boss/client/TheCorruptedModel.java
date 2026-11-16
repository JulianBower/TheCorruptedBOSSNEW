package com.hellgauntlet.boss.client;

import com.hellgauntlet.boss.TheCorruptedBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import org.joml.Quaternionf;

/**
 * The Corrupted: knight model baked from prototype_the_corrupted.bbmodel (no sword). The 52 cubes are stored as
 * quads with the exact per-face UVs of the .bbmodel (CorruptedMesh), grouped in six bones. Head, both arms hang on
 * the torso, so leaning or kneeling the torso moves them along. Model scale (the boss is 3.4 blocks tall) is applied
 * by the renderer.
 */
public class TheCorruptedModel extends EntityModel<TheCorruptedBossEntity> {
    private static final int STRIDE = 23;
    /** Centre of the gauntlet quads in arm-local model pixels (x -1..1.2, y 0.515..3.65). */
    private static final float GAUNTLET_CENTER_X = 0.1F;
    private static final float GAUNTLET_CENTER_Y = 2.0825F;

    /** Rotation (radians) and offset (model pixels) of one bone. */
    private static final class Bone {
        final float[] pivot;
        float xRot, yRot, zRot, xOff, yOff, zOff;

        Bone(float[] pivot) {
            this.pivot = pivot;
        }

        void reset() {
            xRot = yRot = zRot = xOff = yOff = zOff = 0.0F;
        }
    }

    private final Bone torso = new Bone(CorruptedMesh.TORSO_PIVOT);
    private final Bone head = new Bone(CorruptedMesh.HEAD_PIVOT);
    private final Bone rightArm = new Bone(CorruptedMesh.R_ARM_PIVOT);
    private final Bone leftArm = new Bone(CorruptedMesh.L_ARM_PIVOT);
    private final Bone rightLeg = new Bone(CorruptedMesh.R_LEG_PIVOT);
    private final Bone leftLeg = new Bone(CorruptedMesh.L_LEG_PIVOT);

    public TheCorruptedModel() {
        super(RenderType::entityCutoutNoCull);
    }

    @Override
    public void setupAnim(TheCorruptedBossEntity boss, float limbSwing, float limbSwingAmount, float age,
                          float netHeadYaw, float headPitch) {
        torso.reset();
        head.reset();
        rightArm.reset();
        leftArm.reset();
        rightLeg.reset();
        leftLeg.reset();

        float walk = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float swing = Mth.cos(limbSwing * 0.5F) * 1.0F * walk;
        float breathe = Mth.sin(age * 0.09F);

        // idle / walking
        rightLeg.xRot = swing;
        leftLeg.xRot = -swing;
        rightArm.xRot = -swing * 0.6F + breathe * 0.03F;
        leftArm.xRot = swing * 0.6F - breathe * 0.03F;
        rightArm.zRot = 0.06F + breathe * 0.02F;
        leftArm.zRot = -0.06F - breathe * 0.02F;
        torso.yOff = breathe * 0.12F;
        head.yRot = Mth.clamp(netHeadYaw, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
        head.xRot = Mth.clamp(headPitch, -40.0F, 40.0F) * Mth.DEG_TO_RAD;

        switch (boss.getAnimation()) {
            case TheCorruptedBossEntity.ANIM_COMBO -> {
                boolean right = boss.swingingArm != InteractionHand.OFF_HAND;
                float punch = this.attackTime > 0.0F ? Mth.sin(this.attackTime * Mth.PI) : 0.0F;
                rightArm.xRot = -0.8F;
                leftArm.xRot = -0.8F;
                if (right) {
                    rightArm.xRot = -0.8F - 1.0F * punch;
                    torso.yRot = -0.45F * punch;
                } else {
                    leftArm.xRot = -0.8F - 1.0F * punch;
                    torso.yRot = 0.45F * punch;
                }
                torso.xRot = 0.15F;
            }
            case TheCorruptedBossEntity.ANIM_RUPTURE -> {
                rightArm.xRot = -1.5F + Mth.sin(age * 0.8F) * 0.05F;
                leftArm.xRot = -1.5F + Mth.sin(age * 0.8F + 1.0F) * 0.05F;
                torso.xRot = 0.2F;
            }
            case TheCorruptedBossEntity.ANIM_SHOCKWAVE -> {
                rightArm.xRot = -2.7F;
                leftArm.xRot = -2.7F;
                rightArm.zRot = 0.25F;
                leftArm.zRot = -0.25F;
                torso.xRot = -0.12F;
            }
            case TheCorruptedBossEntity.ANIM_DASH -> {
                torso.xRot = 0.55F;
                rightArm.xRot = 0.9F;
                leftArm.xRot = 0.9F;
                head.xRot -= 0.3F;
            }
            default -> {
            }
        }

        if (boss.isCharging()) {
            // right arm (with the gauntlet) straight up, trembling harder as the orb grows
            float progress = boss.getChargeProgress();
            float tremble = Mth.sin(age * 1.7F) * 0.05F * (0.3F + progress);
            rightArm.xRot = -Mth.PI + tremble;
            rightArm.zRot = 0.0F;
            leftArm.xRot = -0.55F;
            leftArm.zRot = -0.2F;
            torso.xRot = -0.1F;
            head.xRot -= 0.25F;
            torso.yOff += Mth.sin(age * 2.3F) * 0.15F * progress;
        } else if (boss.isStunned()) {
            // kneeling: one knee down, torso slumped, head bowed
            torso.yOff = 1.0F;
            leftLeg.yOff = 1.0F;
            rightLeg.yOff = 1.0F;
            rightLeg.xRot = -1.1F;
            leftLeg.xRot = 0.9F;
            torso.xRot = 0.35F;
            head.xRot = 0.45F;
            head.yRot *= 0.3F;
            rightArm.xRot = 0.35F;
            leftArm.xRot = 0.15F;
            rightArm.zRot = 0.1F;
            leftArm.zRot = -0.1F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        poseStack.pushPose();
        place(poseStack, torso, null);
        draw(CorruptedMesh.TORSO.D, poseStack, consumer, light, overlay, color);
        child(poseStack, head, CorruptedMesh.HEAD.D, consumer, light, overlay, color);
        child(poseStack, rightArm, CorruptedMesh.R_ARM.D, consumer, light, overlay, color);
        child(poseStack, leftArm, CorruptedMesh.L_ARM.D, consumer, light, overlay, color);
        poseStack.popPose();

        legs(poseStack, rightLeg, CorruptedMesh.R_LEG.D, consumer, light, overlay, color);
        legs(poseStack, leftLeg, CorruptedMesh.L_LEG.D, consumer, light, overlay, color);
    }

    /** Draws the gauntlet in the transform of the right arm (call with the same pose stack the body was drawn with). */
    public void renderGauntlet(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        poseStack.pushPose();
        place(poseStack, torso, null);
        place(poseStack, rightArm, torso);
        // The baked gauntlet quads come out upside down on the arm: turn them 180 degrees around the forearm
        // (roll about Z keeps the knuckle plate facing forward) and pivot on the gauntlet's own centre, which is
        // also the Y offset that keeps it wrapped around the forearm and hand instead of sliding off.
        poseStack.translate(GAUNTLET_CENTER_X / 16.0F, GAUNTLET_CENTER_Y / 16.0F, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.translate(-GAUNTLET_CENTER_X / 16.0F, -GAUNTLET_CENTER_Y / 16.0F, 0.0F);
        draw(CorruptedMesh.GAUNTLET.D, poseStack, consumer, light, overlay, color);
        poseStack.popPose();
    }

    private void child(PoseStack poseStack, Bone bone, float[] quads, VertexConsumer consumer, int light, int overlay, int color) {
        poseStack.pushPose();
        place(poseStack, bone, torso);
        draw(quads, poseStack, consumer, light, overlay, color);
        poseStack.popPose();
    }

    private void legs(PoseStack poseStack, Bone bone, float[] quads, VertexConsumer consumer, int light, int overlay, int color) {
        poseStack.pushPose();
        place(poseStack, bone, null);
        draw(quads, poseStack, consumer, light, overlay, color);
        poseStack.popPose();
    }

    /** Translate to the bone pivot (relative to its parent, if any), add its offset, then rotate like ModelPart (Z, Y, X). */
    private static void place(PoseStack poseStack, Bone bone, Bone parent) {
        float x = bone.pivot[0] + bone.xOff;
        float y = bone.pivot[1] + bone.yOff;
        float z = bone.pivot[2] + bone.zOff;
        if (parent != null) {
            x -= parent.pivot[0];
            y -= parent.pivot[1];
            z -= parent.pivot[2];
        }
        poseStack.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        if (bone.xRot != 0.0F || bone.yRot != 0.0F || bone.zRot != 0.0F) {
            poseStack.mulPose(new Quaternionf().rotationZYX(bone.zRot, bone.yRot, bone.xRot));
        }
    }

    private static void draw(float[] q, PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < q.length; i += STRIDE) {
            float nx = q[i + 20];
            float ny = q[i + 21];
            float nz = q[i + 22];
            for (int v = 0; v < 4; v++) {
                int o = i + v * 5;
                consumer.addVertex(pose, q[o] / 16.0F, q[o + 1] / 16.0F, q[o + 2] / 16.0F)
                        .setColor(color)
                        .setUv(q[o + 3], q[o + 4])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }
}
