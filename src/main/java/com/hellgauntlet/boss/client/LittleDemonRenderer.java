package com.hellgauntlet.boss.client;

import com.hellgauntlet.boss.LittleDemonEntity;
import com.hellgauntlet.boss.TheCorruptedMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Renders the Little Demon with a full-bright glowing blood-red eye layer. */
public class LittleDemonRenderer extends MobRenderer<LittleDemonEntity, LittleDemonModel> {
    private static final ResourceLocation TEXTURE = tex("little_demon");
    private static final ResourceLocation EYES = tex("little_demon_eyes");

    public LittleDemonRenderer(EntityRendererProvider.Context context) {
        super(context, new LittleDemonModel(context.bakeLayer(LittleDemonModel.LAYER)), 0.3F);
        this.addLayer(new EyesLayer(this));
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(LittleDemonEntity entity) {
        return TEXTURE;
    }

    private static class EyesLayer extends RenderLayer<LittleDemonEntity, LittleDemonModel> {
        EyesLayer(RenderLayerParent<LittleDemonEntity, LittleDemonModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int light, LittleDemonEntity demon, float limbSwing,
                           float limbSwingAmount, float partialTick, float age, float netHeadYaw, float headPitch) {
            if (demon.isInvisible()) {
                return;
            }
            this.getParentModel().renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityTranslucentEmissive(EYES)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        }
    }
}
