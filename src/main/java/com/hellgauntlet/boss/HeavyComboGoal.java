package com.hellgauntlet.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Heavy Gauntlet Combo: three unarmed punches, 10 damage each, the last one a launching finisher. */
public class HeavyComboGoal extends Goal {
    private static final float DAMAGE = 10.0F;
    private static final double START_RANGE = 4.5;
    private static final double REACH = 5.2;
    private static final int[] HIT_TICKS = {12, 26, 42};
    private static final int TOTAL_TICKS = 54;

    private final TheCorruptedBossEntity boss;
    private int tick;

    public HeavyComboGoal(TheCorruptedBossEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.boss.getTarget();
        return target != null && target.isAlive()
                && this.boss.isAttackReady(TheCorruptedBossEntity.Attack.COMBO)
                && this.boss.distanceToSqr(target) <= START_RANGE * START_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.boss.getTarget();
        return this.tick < TOTAL_TICKS && target != null && target.isAlive() && !this.boss.isStunned();
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
        this.boss.beginAttack(TheCorruptedBossEntity.ANIM_COMBO);
    }

    @Override
    public void stop() {
        this.boss.getNavigation().stop();
        this.boss.finishAttack(24);
    }

    @Override
    public void tick() {
        if (this.tick >= TOTAL_TICKS) {
            return;
        }
        this.tick++;
        LivingEntity target = this.boss.getTarget();
        if (target == null) {
            return;
        }
        Vec3 aim = BossCombat.aim(this.boss, target);
        BossCombat.faceDirection(this.boss, aim.x, aim.z);

        if (this.boss.distanceToSqr(target) > 9.0) {
            if (this.tick % 4 == 0) {
                this.boss.getNavigation().moveTo(target, 1.4);
            }
        } else {
            this.boss.getNavigation().stop();
        }

        for (int i = 0; i < HIT_TICKS.length; i++) {
            if (this.tick == HIT_TICKS[i] - 3) {
                this.boss.swing(i % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            }
            if (this.tick == HIT_TICKS[i]) {
                this.punch(aim, i == HIT_TICKS.length - 1);
            }
        }
    }

    private void punch(Vec3 aim, boolean finisher) {
        boolean connected = false;
        for (LivingEntity victim : BossCombat.victimsIn(this.boss, this.boss.getBoundingBox().inflate(REACH, 1.5, REACH))) {
            if (!BossCombat.inCone(this.boss, victim, aim.x, aim.z, REACH, 0.45)) {
                continue;
            }
            if (BossCombat.hit(this.boss, victim, DAMAGE, finisher ? 1.9 : 0.9)) {
                connected = true;
                if (finisher) {
                    BossCombat.launch(victim, 0.35);
                }
            }
        }
        BossCombat.sound(this.boss, SoundEvents.IRON_GOLEM_ATTACK, 1.2F, 0.6F);
        BossCombat.sound(this.boss, connected ? SoundEvents.WARDEN_ATTACK_IMPACT : SoundEvents.PLAYER_ATTACK_SWEEP,
                connected ? 1.2F : 1.0F, connected ? 0.9F : 0.6F);
        if (this.boss.level() instanceof ServerLevel level) {
            double px = this.boss.getX() + aim.x * 2.6;
            double py = this.boss.getY() + 1.8;
            double pz = this.boss.getZ() + aim.z * 2.6;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(BossCombat.CRIMSON, px, py, pz, 10, 0.5, 0.4, 0.5, 0.0);
            if (finisher) {
                BossCombat.fx(this.boss, 3, new Vec3(px, py, pz), new Vec3(aim.x * 0.1, 0.0, aim.z * 0.1));
                level.sendParticles(ParticleTypes.EXPLOSION, px, py - 0.5, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }
}
