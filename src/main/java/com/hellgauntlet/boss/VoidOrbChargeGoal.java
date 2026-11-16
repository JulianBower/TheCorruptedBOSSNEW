package com.hellgauntlet.boss;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Insta-Kill Void Orb Charge. He raises his right arm, locks his gaze on the target and charges the orb for
 * 45 ticks (35 in phase 2). If it finishes, the 35-block shockwave kills every survival player in range.
 * If a player's arrow crosses the orb first, it detonates in his hand instead (self-overload, stun, ORB_HITS++).
 */
public class VoidOrbChargeGoal extends Goal {
    private static final double START_RANGE = 30.0;

    private final TheCorruptedBossEntity boss;
    private int duration;

    public VoidOrbChargeGoal(TheCorruptedBossEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.boss.getTarget();
        return target != null && target.isAlive()
                && this.boss.isOrbReady() && !this.boss.isAttacking()
                && this.boss.distanceToSqr(target) <= START_RANGE * START_RANGE
                && this.boss.hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.boss.getTarget();
        return this.boss.isCharging() && this.boss.isAlive() && !this.boss.isStunned()
                && target != null && target.isAlive();
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
        this.duration = this.boss.getChargeDuration();
        this.boss.beginOrbCharge();
    }

    @Override
    public void stop() {
        this.boss.abortOrbCharge();
    }

    @Override
    public void tick() {
        if (!this.boss.isCharging()) {
            return;
        }
        this.boss.getNavigation().stop();
        LivingEntity target = this.boss.getTarget();
        if (target != null) {
            this.boss.getLookControl().setLookAt(target, 90.0F, 90.0F);
            Vec3 aim = BossCombat.aim(this.boss, target);
            BossCombat.faceDirection(this.boss, aim.x, aim.z);
        }

        AbstractArrow arrow = this.boss.findOrbArrow();
        if (arrow != null) {
            arrow.discard();
            this.boss.overloadOrb();
            return;
        }

        this.boss.advanceCharge();
        if (this.boss.getChargeTicks() >= this.duration) {
            this.boss.releaseKillWave();
        }
    }
}
