package com.hellgauntlet.boss.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.EnumMap;
import java.util.Map;

/** Gives the Steel and Corrupted Knight armor pieces the boss's silhouette via a custom humanoid armor model. */
public final class KnightArmorClientExtensions implements IClientItemExtensions {
    public static final KnightArmorClientExtensions INSTANCE = new KnightArmorClientExtensions();
    private static final Map<EquipmentSlot, CorruptedPlayerArmorModel> MODELS = new EnumMap<>(EquipmentSlot.class);

    private KnightArmorClientExtensions() {}

    public static CorruptedPlayerArmorModel model(EquipmentSlot slot) {
        return MODELS.computeIfAbsent(slot, CorruptedPlayerArmorModel::new);
    }

    @Override
    public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
        CorruptedPlayerArmorModel model = model(slot);
        model.entity = entity;
        return model;
    }
}
