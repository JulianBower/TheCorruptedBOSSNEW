package com.hellgauntlet.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Full-set bonuses.
 * Steel Knight: every 4th hit taken deals no damage.
 * Corrupted Knight: every 3rd hit taken deals no damage, every 2nd melee hit dealt inflicts Corruption (8 s),
 * and the wearer cannot be corrupted.
 * Fused Sovereign Knight: every 2nd hit taken is parried, every 2nd melee hit dealt inflicts Abyssal Corruption
 * (3 hearts of true damage a second, blue lightning), and the wearer cannot be corrupted by either effect.
 */
@EventBusSubscriber(modid = TheCorruptedMod.MODID)
public final class ArmorSetBonuses {
    private static final String STEEL_TAG = "hgb_steel_hits";
    private static final String CORRUPTED_TAKEN_TAG = "hgb_corrupted_taken";
    private static final String CORRUPTED_DEALT_TAG = "hgb_corrupted_dealt";
    private static final String SOVEREIGN_TAKEN_TAG = "hgb_sovereign_taken";
    private static final String SOVEREIGN_DEALT_TAG = "hgb_sovereign_dealt";
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ArmorSetBonuses() {}

    /** 0 = no full set, 1 = Steel Knight, 2 = Corrupted Knight, 3 = Fused Sovereign Knight. */
    public static int fullSet(LivingEntity entity) {
        int tier = -1;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof KnightArmorItem armor)) {
                return 0;
            }
            int t = armor.tier();
            if (tier != -1 && tier != t) {
                return 0;
            }
            tier = t;
        }
        return tier;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide() || event.getAmount() <= 0.0F) {
            return;
        }
        int set = fullSet(player);
        if (set == 0) {
            return;
        }
        String tag = set == 3 ? SOVEREIGN_TAKEN_TAG : set == 2 ? CORRUPTED_TAKEN_TAG : STEEL_TAG;
        int limit = set == 3 ? 2 : set == 2 ? 3 : 4;
        int hits = player.getPersistentData().getInt(tag) + 1;
        if (hits >= limit) {
            player.getPersistentData().putInt(tag, 0);
            event.setCanceled(true);
            BossEnchantments.retaliate(player, event.getSource().getEntity(), set == 3);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (set >= 2) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4F, set == 3 ? 2.0F : 1.9F);
                if (set == 2 && player.level() instanceof ServerLevel level) {
                    level.sendParticles(BossCombat.CRIMSON, player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.4, 0.6, 0.4, 0.0);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
                }
            }
        } else {
            player.getPersistentData().putInt(tag, hits);
        }
    }

    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof Player player) || player.level().isClientSide()
                || event.getSource().getDirectEntity() != player || event.getEntity() == player
                || event.getEntity() instanceof TheCorruptedBossEntity || fullSet(player) < 2) {
            return;
        }
        boolean sovereign = fullSet(player) == 3;
        String tag = sovereign ? SOVEREIGN_DEALT_TAG : CORRUPTED_DEALT_TAG;
        int hits = player.getPersistentData().getInt(tag) + 1;
        if (hits >= 2) {
            player.getPersistentData().putInt(tag, 0);
            if (sovereign) {
                BossEnchantments.applyAbyssalCorruption(player, event.getEntity());
            } else {
                BossEnchantments.applyCorruption(player, event.getEntity());
            }
        } else {
            player.getPersistentData().putInt(tag, hits);
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        var effect = event.getEffectInstance().getEffect();
        int set = fullSet(event.getEntity());
        if (set >= 2 && (effect.is(ModEffects.CORRUPTION.getId()) || (set == 3 && effect.is(ModEffects.ABYSSAL_CORRUPTION.getId())))) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
