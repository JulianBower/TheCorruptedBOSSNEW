package com.hellgauntlet.boss;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/** One piece of the Steel Knight (tier 1) or Corrupted Knight (tier 2) armor. */
public class KnightArmorItem extends ArmorItem {
    private final boolean corrupted;

    public KnightArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties, boolean corrupted) {
        super(material, type, properties);
        this.corrupted = corrupted;
    }

    public boolean isCorrupted() {
        return this.corrupted;
    }

    private static final ResourceLocation STEEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("hellgauntletboss", "textures/entity/the_corrupted_normal.png");
    private static final ResourceLocation CORRUPTED_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("hellgauntletboss", "textures/entity/the_corrupted_awakened.png");

    /** HumanoidArmorLayer binds the boss's own atlas to the 3D knight model instead of a flat vanilla armor layer. */
    @Override
    @Nullable
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return this.corrupted ? CORRUPTED_TEXTURE : STEEL_TEXTURE;
    }
}
