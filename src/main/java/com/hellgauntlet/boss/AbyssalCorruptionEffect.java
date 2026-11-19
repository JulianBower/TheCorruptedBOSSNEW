package com.hellgauntlet.boss;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Abyssal Corruption (Sovereign set bonus): the stronger, blue Corruption. Every second it deals 6.0 HP (3 hearts) of
 * GENERIC_KILL damage, which ignores armor, Protection and Resistance, and every other tick the victim is wrapped in
 * rapid jagged sky-blue lightning (sent to nearby clients as LightningArcPayload).
 */
public class AbyssalCorruptionEffect extends MobEffect {
    public static final int DURATION_TICKS = 160;
    public static final int INTERVAL_TICKS = 20;
    public static final float DAMAGE = 6.0F;

    public AbyssalCorruptionEffect() {
        super(MobEffectCategory.HARMFUL, 0x38BDF8);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // lightning wrap every other tick, damage once a second
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || !(entity.level() instanceof ServerLevel level) || !entity.isAlive()) {
            return true;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return true;
        }
        MobEffectInstance instance = entity.getEffect(ModEffects.ABYSSAL_CORRUPTION);
        int duration = instance != null ? instance.getDuration() : 0;
        if (duration % 2 == 0) {
            wrap(level, entity);
        }
        if (duration % INTERVAL_TICKS == 0) {
            entity.invulnerableTime = 0;
            entity.hurt(entity.damageSources().genericKill(), DAMAGE * (amplifier + 1));
        }
        return true;
    }

    /** Three jagged blue arcs racing around the body between random points of a shell around the hitbox. */
    private static void wrap(ServerLevel level, LivingEntity entity) {
        RandomSource rnd = level.random;
        AABB box = entity.getBoundingBox();
        Vec3 c = box.getCenter();
        double rx = box.getXsize() * 0.5 + 0.25;
        double ry = box.getYsize() * 0.5 + 0.15;
        double rz = box.getZsize() * 0.5 + 0.25;
        for (int i = 0; i < 3; i++) {
            BossEnchantments.arc(level, shell(rnd, c, rx, ry, rz), shell(rnd, c, rx, ry, rz), BossEnchantments.STYLE_BLUE, 0.8F);
        }
    }

    private static Vec3 shell(RandomSource rnd, Vec3 c, double rx, double ry, double rz) {
        Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian());
        d = d.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : d.normalize();
        return c.add(d.x * rx, d.y * ry, d.z * rz);
    }
}
