package com.hellgauntlet.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wearable armor that is the boss: T-visor knight helmet with brow/cheek plates, thick pauldrons, chestplate, waist
 * tassets, knee guards, and the tattered cape. The geometry is TheCorruptedModel's cubes baked to full player scale
 * (ArmorMesh, tools/gen_armor.py) over a dark undersuit (renderUndersuit). One instance exists per armor slot and
 * draws only that slot's quads, following the vanilla humanoid part poses. It is textured by KnightArmorLayer with the
 * boss atlas (the_corrupted_normal / _awakened + _glow), never with a flat vanilla armor texture.
 */
public class CorruptedPlayerArmorModel extends HumanoidModel<LivingEntity> {
    private static final int STRIDE = 23;
    private final EquipmentSlot slot;
    /** Entity being rendered, set by the item extension right before rendering (drives the cape). */
    public LivingEntity entity;

    private final ModelPart cHead, cBody, cRightArm, cLeftArm, cRightLeg, cLeftLeg, cRightBoot, cLeftBoot;

    public CorruptedPlayerArmorModel(EquipmentSlot slot) {
        super(emptyRoot());
        this.slot = slot;
        ModelPart root = cubes().bakeRoot();
        this.cHead = root.getChild("c_head");
        this.cBody = root.getChild("c_body");
        this.cRightArm = root.getChild("c_right_arm");
        this.cLeftArm = root.getChild("c_left_arm");
        this.cRightLeg = root.getChild("c_right_leg");
        this.cLeftLeg = root.getChild("c_left_leg");
        this.cRightBoot = root.getChild("c_right_boot");
        this.cLeftBoot = root.getChild("c_left_boot");
    }

    private static ModelPart emptyRoot() {
        Map<String, ModelPart> children = new HashMap<>();
        for (String name : List.of("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg")) {
            children.put(name, new ModelPart(List.of(), new HashMap<>()));
        }
        return new ModelPart(List.of(), children);
    }

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, int color) {
        switch (this.slot) {
            case HEAD -> {
                part(ps, this.head, ArmorMesh.HEAD.D, vc, light, overlay, color);
            }
            case CHEST -> {
                ps.pushPose();
                this.body.translateAndRotate(ps);
                draw(ArmorMesh.CHEST.D, ps, vc, light, overlay, color);
                cape(ps, vc, light, overlay, color);
                ps.popPose();
                part(ps, this.rightArm, ArmorMesh.R_ARM.D, vc, light, overlay, color);
                part(ps, this.leftArm, ArmorMesh.L_ARM.D, vc, light, overlay, color);
            }
            case LEGS -> {
                part(ps, this.body, ArmorMesh.BODY_LOW.D, vc, light, overlay, color);
                part(ps, this.rightLeg, ArmorMesh.R_LEG_UP.D, vc, light, overlay, color);
                part(ps, this.leftLeg, ArmorMesh.L_LEG_UP.D, vc, light, overlay, color);
            }
            case FEET -> {
                part(ps, this.rightLeg, ArmorMesh.R_LEG_LOW.D, vc, light, overlay, color);
                part(ps, this.leftLeg, ArmorMesh.L_LEG_LOW.D, vc, light, overlay, color);
            }
            default -> {
            }
        }
    }

    /**
     * Dark charcoal (#1E1E22) undersuit for the worn slot: plain player-scale cubes that hide every pixel of skin
     * between the boss plates. KnightArmorLayer draws them with undersuit.png; the plates themselves (renderToBuffer)
     * use the boss atlas 1-to-1 and nothing is painted into the boss texture.
     */
    public void renderUndersuit(PoseStack ps, VertexConsumer vc, int light, int overlay, int color) {
        switch (this.slot) {
            case HEAD -> solid(this.cHead, this.head, ps, vc, light, overlay, color);
            case CHEST -> {
                solid(this.cBody, this.body, ps, vc, light, overlay, color);
                solid(this.cRightArm, this.rightArm, ps, vc, light, overlay, color);
                solid(this.cLeftArm, this.leftArm, ps, vc, light, overlay, color);
            }
            case LEGS -> {
                solid(this.cRightLeg, this.rightLeg, ps, vc, light, overlay, color);
                solid(this.cLeftLeg, this.leftLeg, ps, vc, light, overlay, color);
            }
            case FEET -> {
                solid(this.cRightBoot, this.rightLeg, ps, vc, light, overlay, color);
                solid(this.cLeftBoot, this.leftLeg, ps, vc, light, overlay, color);
            }
            default -> {
            }
        }
    }

    private static void solid(ModelPart cube, ModelPart pose, PoseStack ps, VertexConsumer vc, int light, int overlay, int color) {
        cube.copyFrom(pose);
        cube.render(ps, vc, light, overlay, color);
    }

    private static LayerDefinition cubes() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("c_head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.5F, -8.5F, -4.5F, 9.0F, 9.0F, 9.0F), PartPose.ZERO);
        root.addOrReplaceChild("c_body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.5F, 0.0F, -2.5F, 9.0F, 12.0F, 5.0F), PartPose.ZERO);
        root.addOrReplaceChild("c_right_arm", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.4F, -2.4F, -2.4F, 4.8F, 12.8F, 4.8F), PartPose.ZERO);
        root.addOrReplaceChild("c_left_arm", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.4F, -2.4F, -2.4F, 4.8F, 12.8F, 4.8F), PartPose.ZERO);
        root.addOrReplaceChild("c_right_leg", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.3F, 0.0F, -2.3F, 4.6F, 12.0F, 4.6F), PartPose.ZERO);
        root.addOrReplaceChild("c_left_leg", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.3F, 0.0F, -2.3F, 4.6F, 12.0F, 4.6F), PartPose.ZERO);
        root.addOrReplaceChild("c_right_boot", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.4F, 8.0F, -2.4F, 4.8F, 4.0F, 4.8F), PartPose.ZERO);
        root.addOrReplaceChild("c_left_boot", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.4F, 8.0F, -2.4F, 4.8F, 4.0F, 4.8F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void part(PoseStack ps, ModelPart part, float[] quads, VertexConsumer vc, int light, int overlay, int color) {
        ps.pushPose();
        part.translateAndRotate(ps);
        draw(quads, ps, vc, light, overlay, color);
        ps.popPose();
    }

    /** Flowing cape hanging from the shoulders, swinging out when moving and sneaking. */
    private void cape(PoseStack ps, VertexConsumer vc, int light, int overlay, int color) {
        float angle = 0.1F;
        if (this.entity != null) {
            angle += Mth.clamp(this.entity.walkAnimation.speed(), 0.0F, 1.0F) * 0.5F
                    + Mth.sin(this.entity.tickCount * 0.1F) * 0.03F + (this.entity.isCrouching() ? 0.3F : 0.0F);
        }
        ps.pushPose();
        ps.translate(0.0F, 1.0F / 16.0F, 3.0F / 16.0F);
        ps.mulPose(com.mojang.math.Axis.XP.rotation(angle));
        float[] uv = ArmorMesh.CAPE_UV;
        // two layers (outside / inside) so it is visible from both sides
        box(ps, vc, -4.6F, 0.0F, 0.0F, 4.6F, 16.0F, 0.7F, uv, light, overlay, color);
        ps.popPose();
    }

    private static void box(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                            float[] uv, int light, int overlay, int color) {
        PoseStack.Pose pose = ps.last();
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1},
                {x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1},
                {x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0},
                {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0},
                {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0},
                {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0}};
        float[][] corner = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[2], uv[3]}, {uv[0], uv[3]}};
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) {
                vc.addVertex(pose, f[i * 3] / 16.0F, f[i * 3 + 1] / 16.0F, f[i * 3 + 2] / 16.0F)
                        .setColor(color).setUv(corner[i][0], corner[i][1]).setOverlay(overlay).setLight(light)
                        .setNormal(pose, f[12], f[13], f[14]);
            }
        }
    }

    private static void draw(float[] q, PoseStack ps, VertexConsumer vc, int light, int overlay, int color) {
        PoseStack.Pose pose = ps.last();
        for (int i = 0; i < q.length; i += STRIDE) {
            for (int v = 0; v < 4; v++) {
                int o = i + v * 5;
                vc.addVertex(pose, q[o] / 16.0F, q[o + 1] / 16.0F, q[o + 2] / 16.0F)
                        .setColor(color).setUv(q[o + 3], q[o + 4]).setOverlay(overlay).setLight(light)
                        .setNormal(pose, q[i + 20], q[i + 21], q[i + 22]);
            }
        }
    }
}
