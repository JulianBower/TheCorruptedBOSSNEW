package com.hellgauntlet.boss.client;

import com.hellgauntlet.boss.KnightArmorItem;
import com.hellgauntlet.boss.TheCorruptedMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Emissive pass for worn Corrupted Knight pieces: the_corrupted_glow at full bright, so the crimson T-visor and veins
 * glow like on the boss. The base textures come from KnightArmorItem#getArmorTexture (boss atlas) through the vanilla
 * armor layer; players and every other humanoid-model mob get this layer (see ClientSetup#onAddLayers).
 */
public class KnightArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public KnightArmorLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float age, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof KnightArmorItem armor) || armor.getEquipmentSlot() != slot) {
                continue;
            }
            CorruptedPlayerArmorModel model = KnightArmorClientExtensions.model(slot);
            model.entity = entity;
            ((HumanoidModel) this.getParentModel()).copyPropertiesTo(model);
            if (armor.glowTexture() != null) {
                model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityTranslucentEmissive(armor.glowTexture())),
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
            }
        }
    }
}
