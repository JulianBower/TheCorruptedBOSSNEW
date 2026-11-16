package com.hellgauntlet.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Void Rupture Line: the boss slams his gauntlet into the ground and a 16-block line of erupting crimson
 * particles and red lightning races away from him (2 blocks per tick), launching anything it catches.
 */
public class VoidRuptureGoal extends Goal {
    private static final float DAMAGE = 8.0F;
    private static final double START_RANGE = 18.0;
    private static final double START_OFFSET = 1.8;
    private static final int LOCK_TICK = 8;
    private static final int SLAM_TICK = 18;
    private static final int LINE_TICKS = 8;
    private static final double STEP = 2.0; // blocks per tick, 8 ticks = 16 blocks
    private static final int TOTAL_TICKS = SLAM_TICK + LINE_TICKS + 6;

    private final TheCorruptedBossEntity boss;
    private final Set<UUID> struck = new HashSet<>();
    private int tick;
    private double dirX;
    private double dirZ;

    public VoidRuptureGoal(TheCorruptedBossEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.boss.getTarget();
        return target != null && target.isAlive()
                && this.boss.isAttackReady(TheCorruptedBossEntity.Attack.RUPTURE)
                && this.boss.distanceToSqr(target) <= START_RANGE * START_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return this.tick < TOTAL_TICKS && !this.boss.isStunned() && this.boss.getTarget() != null;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.tick = 0;
        this.struck.clear();
        this.boss.beginAttack(TheCorruptedBossEntity.ANIM_RUPTURE);
        this.boss.getNavigation().stop();
        LivingEntity target = this.boss.getTarget();
        if (target != null) {
            this.aimAt(target);
        }
    }

    @Override
    public void stop() {
        this.boss.finishAttack(36);
    }

    private void aimAt(LivingEntity target) {
        Vec3 aim = BossCombat.aim(this.boss, target);
        this.dirX = aim.x;
        this.dirZ = aim.z;
    }

    @Override
    public void tick() {
        if (this.tick >= TOTAL_TICKS) {
            return;
        }
        this.tick++;
        this.boss.getNavigation().stop();
        LivingEntity target = this.boss.getTarget();
        if (this.tick <= LOCK_TICK && target != null) {
            this.aimAt(target);
        }
        BossCombat.faceDirection(this.boss, this.dirX, this.dirZ);
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }

        if (this.tick > LOCK_TICK && this.tick < SLAM_TICK && this.tick % 2 == 0) {
            this.telegraph(level);
        }
        if (this.tick == SLAM_TICK - 3) {
            this.boss.swing(InteractionHand.MAIN_HAND);
        }
        if (this.tick == SLAM_TICK) {
            this.slam(level);
        }
        if (this.tick >= SLAM_TICK && this.tick < SLAM_TICK + LINE_TICKS) {
            this.advanceLine(level, this.tick - SLAM_TICK);
        }
    }

    private double startX() {
        return this.boss.getX() + this.dirX * START_OFFSET;
    }

    private double startZ() {
        return this.boss.getZ() + this.dirZ * START_OFFSET;
    }

    private void telegraph(ServerLevel level) {
        for (double d = 0.0; d <= LINE_TICKS * STEP; d += 2.0) {
            double x = this.startX() + this.dirX * d;
            double z = this.startZ() + this.dirZ * d;
            double y = BossCombat.surfaceY(level, x, this.boss.getY(), z);
            level.sendParticles(BossCombat.CRIMSON, x, y + 0.1, z, 1, 0.1, 0.0, 0.1, 0.0);
            level.sendParticles(ParticleTypes.SMOKE, x, y + 0.1, z, 1, 0.1, 0.0, 0.1, 0.0);
        }
    }

    private void slam(ServerLevel level) {
        double fx = this.startX();
        double fz = this.startZ();
        double fy = BossCombat.surfaceY(level, fx, this.boss.getY(), fz);
        BossCombat.fx(this.boss, 2, new Vec3(fx, fy, fz), new Vec3(this.dirX, 0.0, this.dirZ));
        BossCombat.sound(this.boss, SoundEvents.WARDEN_ATTACK_IMPACT, 1.5F, 0.6F);
        BossCombat.sound(this.boss, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.4F);
        level.sendParticles(ParticleTypes.EXPLOSION, fx, fy + 0.3, fz, 1, 0.0, 0.0, 0.0, 0.0);
        BossCombat.redBolt(level, fx, fy, fz, 4.0);
    }

    private void advanceLine(ServerLevel level, int index) {
        double from = index * STEP;
        double to = from + STEP;
        double sx = this.startX();
        double sz = this.startZ();

        for (double d = from; d < to; d += 0.5) {
            double x = sx + this.dirX * d;
            double z = sz + this.dirZ * d;
            double y = BossCombat.surfaceY(level, x, this.boss.getY(), z);
            for (double h = 0.2; h <= 2.7; h += 0.5) {
                level.sendParticles(BossCombat.CRIMSON, x, y + h, z, 1, 0.25, 0.1, 0.25, 0.0);
            }
            level.sendParticles(BossCombat.CRIMSON, x, y + 0.3, z, 3, 0.4, 0.2, 0.4, 0.1);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.4, z, 1, 0.3, 0.2, 0.3, 0.05);
            if (((int) (d * 2.0)) % 2 == 0) {
                BossCombat.redBolt(level, x, y, z, 3.0);
            }
        }
        double mx = sx + this.dirX * (from + STEP / 2.0);
        double mz = sz + this.dirZ * (from + STEP / 2.0);
        BossCombat.sound(level, new Vec3(mx, this.boss.getY(), mz), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.6F);

        double x0 = sx + this.dirX * from;
        double z0 = sz + this.dirZ * from;
        double x1 = sx + this.dirX * to;
        double z1 = sz + this.dirZ * to;
        AABB area = new AABB(Math.min(x0, x1) - 1.5, this.boss.getY() - 3.0, Math.min(z0, z1) - 1.5,
                Math.max(x0, x1) + 1.5, this.boss.getY() + 4.0, Math.max(z0, z1) + 1.5);
        for (LivingEntity victim : BossCombat.victimsIn(this.boss, area)) {
            if (this.struck.contains(victim.getUUID())) {
                continue;
            }
            double rx = victim.getX() - sx;
            double rz = victim.getZ() - sz;
            double along = rx * this.dirX + rz * this.dirZ;
            double across = Math.abs(rx * this.dirZ - rz * this.dirX);
            if (along < from - 0.5 || along > to + 0.5 || across > 1.4) {
                continue;
            }
            if (BossCombat.heightAboveGround(victim) > 2.5) {
                continue;
            }
            this.struck.add(victim.getUUID());
            if (BossCombat.hit(this.boss, victim, DAMAGE, 0.0)) {
                BossCombat.launch(victim, 1.15);
                level.sendParticles(BossCombat.CRIMSON, victim.getX(), victim.getY() + 0.5, victim.getZ(), 16, 0.4, 0.6, 0.4, 0.05);
            }
        }
    }
}
