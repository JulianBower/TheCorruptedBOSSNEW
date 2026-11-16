package com.hellgauntlet.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TheCorruptedMod.MODID);

    public static final DeferredItem<DeferredSpawnEggItem> THE_CORRUPTED_SPAWN_EGG =
            ITEMS.register("the_corrupted_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.THE_CORRUPTED, 0x1A0505, 0xD91E18, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> LITTLE_DEMON_SPAWN_EGG =
            ITEMS.register("little_demon_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.LITTLE_DEMON, 0x1C1A1E, 0xD91E18, new Item.Properties()));

    /** Dropped once by The Corrupted; replaces the netherite ingot in the Gauntlet of Hell recipe. */
    public static final DeferredItem<Item> CORRUPTED_CORE =
            ITEMS.register("corrupted_core", () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredItem<Item> STEEL_INGOT =
            ITEMS.register("steel_ingot", () -> new Item(new Item.Properties()));

    // Tier 1: Steel Knight (durability multiplier 28)
    public static final DeferredItem<KnightArmorItem> STEEL_HELMET = armor("steel_helmet", ModArmorMaterials.STEEL, ArmorItem.Type.HELMET, 28, false);
    public static final DeferredItem<KnightArmorItem> STEEL_CHESTPLATE = armor("steel_chestplate", ModArmorMaterials.STEEL, ArmorItem.Type.CHESTPLATE, 28, false);
    public static final DeferredItem<KnightArmorItem> STEEL_LEGGINGS = armor("steel_leggings", ModArmorMaterials.STEEL, ArmorItem.Type.LEGGINGS, 28, false);
    public static final DeferredItem<KnightArmorItem> STEEL_BOOTS = armor("steel_boots", ModArmorMaterials.STEEL, ArmorItem.Type.BOOTS, 28, false);

    public static final DeferredItem<SmithingTemplateItem> CORRUPTED_UPGRADE_TEMPLATE =
            ITEMS.register("corrupted_upgrade_smithing_template", ModItems::createCorruptedTemplate);

    // Tier 2: Corrupted Knight (durability multiplier 45)
    public static final DeferredItem<KnightArmorItem> CORRUPTED_HELMET = armor("corrupted_helmet", ModArmorMaterials.CORRUPTED, ArmorItem.Type.HELMET, 45, true);
    public static final DeferredItem<KnightArmorItem> CORRUPTED_CHESTPLATE = armor("corrupted_chestplate", ModArmorMaterials.CORRUPTED, ArmorItem.Type.CHESTPLATE, 45, true);
    public static final DeferredItem<KnightArmorItem> CORRUPTED_LEGGINGS = armor("corrupted_leggings", ModArmorMaterials.CORRUPTED, ArmorItem.Type.LEGGINGS, 45, true);
    public static final DeferredItem<KnightArmorItem> CORRUPTED_BOOTS = armor("corrupted_boots", ModArmorMaterials.CORRUPTED, ArmorItem.Type.BOOTS, 45, true);

    private ModItems() {}

    private static DeferredItem<KnightArmorItem> armor(String name, Holder<ArmorMaterial> material, ArmorItem.Type type, int durabilityMultiplier, boolean corrupted) {
        return ITEMS.register(name, () -> new KnightArmorItem(material, type,
                corrupted ? new Item.Properties().durability(type.getDurability(durabilityMultiplier)).rarity(Rarity.EPIC).fireResistant()
                        : new Item.Properties().durability(type.getDurability(durabilityMultiplier)),
                corrupted));
    }

    private static MutableComponent text(String prefix, String key) {
        return Component.translatable(Util.makeDescriptionId(prefix, ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, key)));
    }

    private static SmithingTemplateItem createCorruptedTemplate() {
        List<ResourceLocation> armorSlots = List.of(
                ResourceLocation.withDefaultNamespace("item/empty_armor_slot_helmet"),
                ResourceLocation.withDefaultNamespace("item/empty_armor_slot_chestplate"),
                ResourceLocation.withDefaultNamespace("item/empty_armor_slot_leggings"),
                ResourceLocation.withDefaultNamespace("item/empty_armor_slot_boots"));
        List<ResourceLocation> additionSlots = List.of(ResourceLocation.withDefaultNamespace("item/empty_slot_ingot"));
        return new SmithingTemplateItem(
                text("item", "smithing_template.corrupted_upgrade.applies_to").withStyle(ChatFormatting.BLUE),
                text("item", "smithing_template.corrupted_upgrade.ingredients").withStyle(ChatFormatting.BLUE),
                text("upgrade", "corrupted_upgrade").withStyle(ChatFormatting.GRAY),
                text("item", "smithing_template.corrupted_upgrade.base_slot_description"),
                text("item", "smithing_template.corrupted_upgrade.additions_slot_description"),
                armorSlots, additionSlots);
    }
}
