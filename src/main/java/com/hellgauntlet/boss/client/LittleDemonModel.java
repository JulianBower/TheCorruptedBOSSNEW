package com.hellgauntlet.boss.client;

import com.hellgauntlet.boss.LittleDemonEntity;
import com.hellgauntlet.boss.TheCorruptedMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Miniature horned imp (about 1.15 blocks tall): head with two horns, arms, legs, a tail and a tattered cloak. */
public class LittleDemonModel extends EntityModel<LittleDemonEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "little_demon"), "main");

    private final ModelPart root, head, body, rightArm, leftArm, rightLeg, leftLeg, tail, cloak;

    public LittleDemonModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.cloak = this.body.getChild("cloak");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.tail = root.getChild("tail");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.offset(0.0F, 12.0F, 0.0F));
        head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(32, 0)
                .addBox(-0.5F, -3.5F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(2.2F, -5.5F, 0.0F, 0.0F, 0.0F, 0.4F));
        head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(40, 0)
                .addBox(-0.5F, -3.5F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-2.2F, -5.5F, 0.0F, 0.0F, 0.0F, -0.4F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-3.0F, 0.0F, -2.0F, 6.0F, 7.0F, 4.0F), PartPose.offset(0.0F, 12.0F, 0.0F));
        body.addOrReplaceChild("cloak", CubeListBuilder.create().texOffs(0, 44)
                .addBox(-3.5F, -0.5F, 0.0F, 7.0F, 9.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.3F, 0.12F, 0.0F, 0.0F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(24, 16)
                .addBox(-2.0F, -0.5F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offset(-3.0F, 12.5F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16)
                .addBox(0.0F, -0.5F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offset(3.0F, 12.5F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.offset(-1.5F, 19.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(12, 32)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.offset(1.5F, 19.0F, 0.0F));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(24, 32)
                .addBox(-0.5F, 0.0F, 0.0F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 17.0F, 2.0F, 1.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(LittleDemonEntity demon, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        float swing = Mth.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;
        this.rightLeg.xRot = swing;
        this.leftLeg.xRot = -swing;
        this.rightArm.xRot = -swing * 0.8F;
        this.leftArm.xRot = swing * 0.8F;
        this.rightArm.zRot = 0.08F + Mth.cos(age * 0.09F) * 0.04F;
        this.leftArm.zRot = -0.08F - Mth.cos(age * 0.09F) * 0.04F;
        this.tail.yRot = Mth.sin(age * 0.15F) * 0.35F;
        this.cloak.xRot = 0.12F + Mth.abs(swing) * 0.25F + Mth.sin(age * 0.1F) * 0.03F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        this.root.render(poseStack, consumer, light, overlay, color);
    }
}
