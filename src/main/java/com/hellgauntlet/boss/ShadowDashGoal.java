package com.hellgauntlet.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Phase 2 Shadow Dash: a short windup, a rapid straight dash that leaves red-black smoke behind him, then a
 * heavy-knockback uppercut where he stops.
 */
public class ShadowDashGoal extends Goal {
    private static final float DAMAGE = 14.0F;
    private static final double MIN_RANGE = 5.0;
    private static final double MAX_RANGE = 22.0;
    private static final int WINDUP_TICKS = 8;
    private static final int MAX_DASH_TICKS = 12;
    private static final double DASH_SPEED = 1.5;
    private static final double STRIKE_RANGE = 2.8;
    private static final double UPPERCUT_REACH = 4.2;
    private static final int STRIKE_TICKS = 10;

    private enum State { WINDUP, DASH, STRIKE }

    private final TheCorruptedBossEntity boss;
    private State state = State.WINDUP;
    private int timer;
    private boolean done;
    private double dirX;
    private double dirZ;

    public ShadowDashGoal(TheCorruptedBossEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.boss.getTarget();
        if (target == null || !target.isAlive() || !this.boss.isPhaseTwo()
                || !this.boss.isAttackReady(TheCorruptedBossEntity.Attack.DASH)) {
            return false;
        }
        double distSq = this.boss.distanceToSqr(target);
        return distSq >= MIN_RANGE * MIN_RANGE && distSq <= MAX_RANGE * MAX_RANGE && this.boss.hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        return !this.done && !this.boss.isStunned() && this.boss.getTarget() != null;
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
        this.state = State.WINDUP;
        this.timer = 0;
        this.done = false;
        this.boss.beginAttack(TheCorruptedBossEntity.ANIM_DASH);
        this.boss.getNavigation().stop();
        LivingEntity target = this.boss.getTarget();
        if (target != null) {
            this.aimAt(target);
        }
    }

    @Override
    public void stop() {
        this.boss.finishAttack(45);
    }

    private void aimAt(LivingEntity target) {
        Vec3 aim = BossCombat.aim(this.boss, target);
        this.dirX = aim.x;
        this.dirZ = aim.z;
    }

    @Override
    public void tick() {
        if (this.done || !(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        this.timer++;
        LivingEntity target = this.boss.getTarget();

        switch (this.state) {
            case WINDUP -> {
                this.boss.getNavigation().stop();
                if (target != null) {
                    this.aimAt(target);
                }
                BossCombat.faceDirection(this.boss, this.dirX, this.dirZ);
                level.sendParticles(BossCombat.SHADOW, this.boss.getX(), this.boss.getY() + 0.2, this.boss.getZ(),
                        6, 0.6, 0.1, 0.6, 0.0);
                level.sendParticles(BossCombat.CRIMSON, this.boss.getX(), this.boss.getY() + 1.0, this.boss.getZ(),
                        3, 0.5, 0.8, 0.5, 0.0);
                if (this.timer >= WINDUP_TICKS) {
                    this.state = State.DASH;
                    this.timer = 0;
                    BossCombat.sound(this.boss, SoundEvents.WARDEN_SONIC_BOOM, 1.2F, 1.6F);
                    BossCombat.fx(this.boss, 3, this.boss.position(), new Vec3(this.dirX * 12.0, 0.0, this.dirZ * 12.0));
                }
            }
            case DASH -> {
                this.boss.getNavigation().stop();
                BossCombat.faceDirection(this.boss, this.dirX, this.dirZ);
                this.boss.setDeltaMovement(this.dirX * DASH_SPEED, this.boss.getDeltaMovement().y, this.dirZ * DASH_SPEED);
                this.trail(level);
                boolean close = false;
                if (target != null) {
                    double dx = target.getX() - this.boss.getX();
                    double dz = target.getZ() - this.boss.getZ();
                    close = dx * dx + dz * dz <= STRIKE_RANGE * STRIKE_RANGE;
                }
                if (close || this.timer >= MAX_DASH_TICKS || (this.boss.horizontalCollision && this.timer > 2)) {
                    this.state = State.STRIKE;
                    this.timer = 0;
                    this.boss.setDeltaMovement(0.0, this.boss.getDeltaMovement().y, 0.0);
                }
            }
            case STRIKE -> {
                this.boss.getNavigation().stop();
                if (this.timer == 1) {
                    if (target != null) {
                        this.aimAt(target);
                    }
                    BossCombat.faceDirection(this.boss, this.dirX, this.dirZ);
                    this.boss.swing(InteractionHand.MAIN_HAND);
                    this.uppercut(level);
                }
                if (this.timer >= STRIKE_TICKS) {
                    this.done = true;
                }
            }
        }
    }

    private void trail(ServerLevel level) {
        double x = this.boss.getX();
        double y = this.boss.getY();
        double z = this.boss.getZ();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.4, z, 4, 0.4, 0.9, 0.4, 0.02);
        level.sendParticles(BossCombat.SHADOW, x, y + 1.4, z, 6, 0.5, 1.0, 0.5, 0.0);
        level.sendParticles(BossCombat.CRIMSON, x, y + 1.4, z, 3, 0.5, 1.0, 0.5, 0.0);
    }

    private void uppercut(ServerLevel level) {
        boolean connected = false;
        for (LivingEntity victim : BossCombat.victimsIn(this.boss,
                this.boss.getBoundingBox().inflate(UPPERCUT_REACH, 1.5, UPPERCUT_REACH))) {
            if (!BossCombat.inCone(this.boss, victim, this.dirX, this.dirZ, UPPERCUT_REACH, 0.2)) {
                continue;
            }
            if (BossCombat.hit(this.boss, victim, DAMAGE, 2.2)) {
                BossCombat.launch(victim, 1.05);
                connected = true;
            }
        }
        double px = this.boss.getX() + this.dirX * 2.4;
        double py = this.boss.getY() + 1.4;
        double pz = this.boss.getZ() + this.dirZ * 2.4;
        BossCombat.sound(this.boss, SoundEvents.WARDEN_ATTACK_IMPACT, 1.6F, connected ? 0.7F : 0.9F);
        BossCombat.sound(this.boss, SoundEvents.IRON_GOLEM_ATTACK, 1.2F, 0.5F);
        BossCombat.fx(this.boss, 3, new Vec3(px, py, pz), new Vec3(this.dirX * 0.1, 0.0, this.dirZ * 0.1));
        level.sendParticles(ParticleTypes.EXPLOSION, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(BossCombat.CRIMSON, px, py + 0.5, pz, 18, 0.6, 0.8, 0.6, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, px, py, pz, 8, 0.5, 0.6, 0.5, 0.05);
    }
}
