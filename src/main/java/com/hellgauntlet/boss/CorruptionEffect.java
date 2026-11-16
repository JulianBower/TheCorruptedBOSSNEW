package com.hellgauntlet.boss;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Corruption: 8 seconds, 1.5 damage every second. The damage uses GENERIC_KILL, which is tagged
 * bypasses_armor, bypasses_enchantments' protection (via bypasses_invulnerability) and bypasses_resistance,
 * so armor, Protection enchantments and Resistance do nothing against it.
 */
public class CorruptionEffect extends MobEffect {
    public static final int DURATION_TICKS = 160;
    public static final int INTERVAL_TICKS = 20;
    public static final float DAMAGE = 1.5F;

    public CorruptionEffect() {
        super(MobEffectCategory.HARMFUL, 0xD91E18);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % INTERVAL_TICKS == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || !entity.isAlive()) {
            return true;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return true;
        }
        entity.invulnerableTime = 0; // hurt cooldown must not eat the tick
        entity.hurt(entity.damageSources().genericKill(), DAMAGE * (amplifier + 1));
        return true;
    }
}
