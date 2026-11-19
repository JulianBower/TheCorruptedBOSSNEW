package com.hellgauntlet.boss;

import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, TheCorruptedMod.MODID);

    /** Enchantability 15 (accepts table and anvil enchants). 3/8/6/3 = 20 armor, toughness 3, knockback resistance 0.1 (durability multiplier 28 is set on the items). */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STEEL = ARMOR_MATERIALS.register("steel",
            () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                        m.put(ArmorItem.Type.BOOTS, 3);
                        m.put(ArmorItem.Type.LEGGINGS, 6);
                        m.put(ArmorItem.Type.CHESTPLATE, 8);
                        m.put(ArmorItem.Type.HELMET, 3);
                        m.put(ArmorItem.Type.BODY, 11);
                    }),
                    15, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(ModItems.STEEL_INGOT.get()),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "steel"))),
                    3.0F, 0.1F));

    /** Enchantability 18 (accepts table and anvil enchants). 4/9/7/4 = 24 armor, toughness 3.5, knockback resistance 0.15 (durability multiplier 45 is set on the items). */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CORRUPTED = ARMOR_MATERIALS.register("corrupted",
            () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                        m.put(ArmorItem.Type.BOOTS, 4);
                        m.put(ArmorItem.Type.LEGGINGS, 7);
                        m.put(ArmorItem.Type.CHESTPLATE, 9);
                        m.put(ArmorItem.Type.HELMET, 4);
                        m.put(ArmorItem.Type.BODY, 13);
                    }),
                    18, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(ModItems.CORRUPTED_CORE.get()),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "corrupted"))),
                    3.5F, 0.15F));

    /** Fused Sovereign Knight: +6 armor over Corrupted (5/9/11/5 = 30 over 4/7/9/4 = 24), toughness 4, knockback resistance 0.2. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SOVEREIGN = ARMOR_MATERIALS.register("sovereign",
            () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                        m.put(ArmorItem.Type.BOOTS, 5);
                        m.put(ArmorItem.Type.LEGGINGS, 9);
                        m.put(ArmorItem.Type.CHESTPLATE, 11);
                        m.put(ArmorItem.Type.HELMET, 5);
                        m.put(ArmorItem.Type.BODY, 15);
                    }),
                    22, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(ModItems.CORRUPTED_CORE.get()),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "sovereign"))),
                    4.0F, 0.2F));

    private ModArmorMaterials() {}
}
