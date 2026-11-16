package com.hellgauntlet.boss;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Walks the boss toward his target whenever no attack is running. */
public class ChaseTargetGoal extends Goal {
    private final TheCorruptedBossEntity boss;
    private int repath;

    public ChaseTargetGoal(TheCorruptedBossEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.boss.getTarget();
        return target != null && target.isAlive()
                && !this.boss.isStunned() && !this.boss.isAttacking() && !this.boss.isCharging();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.repath = 0;
    }

    @Override
    public void stop() {
        this.boss.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.boss.getTarget();
        if (target == null) {
            return;
        }
        this.boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distSq = this.boss.distanceToSqr(target);
        if (distSq > 9.0) {
            if (--this.repath <= 0) {
                this.repath = 6;
                this.boss.getNavigation().moveTo(target, distSq > 100.0 ? 1.25 : 1.0);
            }
        } else {
            this.boss.getNavigation().stop();
        }
    }
}
