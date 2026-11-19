package com.hellgauntlet.boss;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/** One piece of the Steel Knight (tier 1), Corrupted Knight (tier 2) or Fused Sovereign Knight (tier 3) armor. */
public class KnightArmorItem extends ArmorItem {
    private static final String NS = "hellgauntletboss";
    private static final ResourceLocation STEEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(NS, "textures/entity/the_corrupted_normal.png");
    private static final ResourceLocation CORRUPTED_TEXTURE = ResourceLocation.fromNamespaceAndPath(NS, "textures/entity/the_corrupted_awakened.png");
    private static final ResourceLocation SOVEREIGN_TEXTURE = ResourceLocation.fromNamespaceAndPath(NS, "textures/entity/the_corrupted_sovereign.png");
    private static final ResourceLocation CORRUPTED_GLOW = ResourceLocation.fromNamespaceAndPath(NS, "textures/entity/the_corrupted_glow.png");
    private static final ResourceLocation SOVEREIGN_GLOW = ResourceLocation.fromNamespaceAndPath(NS, "textures/entity/the_corrupted_sovereign_glow.png");

    private final int tier;

    public KnightArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties, boolean corrupted) {
        this(material, type, properties, corrupted ? 2 : 1);
    }

    public KnightArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties, int tier) {
        super(material, type, properties);
        this.tier = tier;
    }

    /** 1 = Steel, 2 = Corrupted, 3 = Sovereign. */
    public int tier() {
        return this.tier;
    }

    /** True for Corrupted and Sovereign pieces (they glow). */
    public boolean isCorrupted() {
        return this.tier >= 2;
    }

    /** Full-bright emissive texture drawn over worn pieces, or null for Steel. */
    @Nullable
    public ResourceLocation glowTexture() {
        return this.tier == 3 ? SOVEREIGN_GLOW : this.tier == 2 ? CORRUPTED_GLOW : null;
    }

    /** HumanoidArmorLayer binds the boss's own atlas to the 3D knight model instead of a flat vanilla armor layer. */
    @Override
    @Nullable
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return this.tier == 3 ? SOVEREIGN_TEXTURE : this.tier == 2 ? CORRUPTED_TEXTURE : STEEL_TEXTURE;
    }
}
