package com.hellgauntlet.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import java.util.List;

/** Shared helpers for the boss attacks: damage, Corruption, knockback, ground checks and particle effects. */
public final class BossCombat {
    /** Red lightning bolt particle (client draws a jagged bolt, same look as the Corruption effect). */
    public static final ParticleOptions CRIMSON = ModParticles.CORRUPTION_BOLT.get();
    public static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.04F, 0.0F, 0.02F), 2.0F);

    private BossCombat() {}

    /** Tells every client tracking `source` to draw the red lightning of an attack (see CorruptionAttackFxPayload). */
    public static void fx(Entity source, int attackType, Vec3 origin, Vec3 direction) {
        if (!source.level().isClientSide()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(source,
                    new CorruptionAttackFxPayload(attackType, origin.x, origin.y, origin.z, direction.x, direction.y, direction.z));
        }
    }

    // ---------------------------------------------------------------- sound

    public static void sound(Entity source, SoundEvent sound, float volume, float pitch) {
        source.level().playSound(null, source.getX(), source.getY(), source.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    public static void sound(Level level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ---------------------------------------------------------------- targeting

    /** Survival/adventure players, plus whatever the boss currently targets. */
    public static boolean isVictim(TheCorruptedBossEntity boss, LivingEntity e) {
        if (e == boss || !e.isAlive()) {
            return false;
        }
        if (e instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return e == boss.getTarget();
    }

    public static List<LivingEntity> victimsIn(TheCorruptedBossEntity boss, AABB area) {
        return boss.level().getEntitiesOfClass(LivingEntity.class, area, e -> isVictim(boss, e));
    }

    /** Horizontal unit vector from one entity to another (falls back to the boss's facing). */
    public static Vec3 aim(Entity from, Entity to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            float yaw = from.getYRot() * Mth.DEG_TO_RAD;
            return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        }
        return new Vec3(dx / len, 0.0, dz / len);
    }

    /** Snap body, head and entity rotation toward a horizontal direction. */
    public static void faceDirection(Mob mob, double dx, double dz) {
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.yHeadRot = yaw;
    }

    /** True if the victim is within reach and in front of the given horizontal direction. */
    public static boolean inCone(Entity source, LivingEntity victim, double ax, double az, double reach, double minDot) {
        double dx = victim.getX() - source.getX();
        double dz = victim.getZ() - source.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist - victim.getBbWidth() / 2.0 > reach) {
            return false;
        }
        if (dist < 1.6) {
            return true;
        }
        return (dx * ax + dz * az) / dist >= minDot;
    }

    // ---------------------------------------------------------------- damage

    public static void corrupt(LivingEntity target) {
        target.addEffect(new MobEffectInstance(ModEffects.CORRUPTION, CorruptionEffect.DURATION_TICKS, 0));
    }

    /** Boss hit: damage, optional knockback away from the boss, and 8s of Corruption if the damage landed. */
    public static boolean hit(TheCorruptedBossEntity boss, LivingEntity target, float damage, double knockback) {
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(boss.damageSources().mobAttack(boss), damage);
        if (hurt) {
            if (knockback > 0.0) {
                target.knockback(knockback, boss.getX() - target.getX(), boss.getZ() - target.getZ());
            }
            corrupt(target);
        }
        return hurt;
    }

    public static void launch(LivingEntity target, double upward) {
        Vec3 v = target.getDeltaMovement();
        target.setDeltaMovement(v.x, Math.max(v.y, upward), v.z);
        target.hasImpulse = true;
        target.hurtMarked = true;
    }

    public static void pushAway(Entity source, LivingEntity target, double strength, double lift) {
        double dx = target.getX() - source.getX();
        double dz = target.getZ() - source.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            dx = 1.0;
            dz = 0.0;
            len = 1.0;
        }
        target.setDeltaMovement(target.getDeltaMovement().add(dx / len * strength, lift, dz / len * strength));
        target.hasImpulse = true;
        target.hurtMarked = true;
    }

    // ---------------------------------------------------------------- ground

    /** How far the entity's feet are above the floor below it (large if there is none close by). */
    public static double heightAboveGround(Entity e) {
        Level level = e.level();
        BlockPos.MutableBlockPos pos = e.blockPosition().mutable();
        for (int i = 0; i < 5; i++) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return e.getY() - (pos.getY() + shape.max(Direction.Axis.Y));
            }
            pos.move(Direction.DOWN);
        }
        return 8.0;
    }

    /** Y of the walkable surface in the column at (x, z), searching down from just above y. */
    public static double surfaceY(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y + 1.0, z).mutable();
        for (int i = 0; i < 8; i++) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return pos.getY() + shape.max(Direction.Axis.Y);
            }
            pos.move(Direction.DOWN);
        }
        return y;
    }

    // ---------------------------------------------------------------- particles

    /** A jagged red lightning bolt drawn from particles. */
    public static void boltBetween(ServerLevel level, Vec3 a, Vec3 b) {
        RandomSource random = level.random;
        Vec3 d = b.subtract(a);
        int segments = Math.max(3, (int) (d.length() * 2.0));
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double jitter = (i == 0 || i == segments) ? 0.0 : 0.7;
            double x = a.x + d.x * t + (random.nextDouble() - 0.5) * jitter;
            double y = a.y + d.y * t + (random.nextDouble() - 0.5) * jitter * 0.4;
            double z = a.z + d.z * t + (random.nextDouble() - 0.5) * jitter;
            level.sendParticles(CRIMSON, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.05, 0.05, 0.05, 0.02);
            }
        }
    }

    public static void redBolt(ServerLevel level, double x, double y, double z, double height) {
        boltBetween(level, new Vec3(x, y, z), new Vec3(x, y + height, z));
    }

    /** Random black/crimson particles on the surface of a sphere. */
    public static void sphereShell(ServerLevel level, Vec3 center, double radius, int count) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            double x = random.nextGaussian();
            double y = random.nextGaussian();
            double z = random.nextGaussian();
            double len = Math.sqrt(x * x + y * y + z * z);
            if (len < 1.0E-4) {
                continue;
            }
            double s = radius / len;
            level.sendParticles(i % 3 == 0 ? CRIMSON : SHADOW,
                    center.x + x * s, center.y + y * s, center.z + z * s, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
