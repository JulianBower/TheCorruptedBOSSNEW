package com.hellgauntlet.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Jump-Dodgeable Shockwave Ring: 15 ticks of windup, then a ground-level ring of red lightning expands out to
 * 10 blocks. It only hurts targets whose feet are on (or just above) the ground, so jumping over it works.
 */
public class ShockwaveRingGoal extends Goal {
    private static final float DAMAGE = 9.0F;
    private static final double START_RANGE = 11.0;
    private static final int WINDUP_TICKS = 15;
    private static final int RING_TICKS = 18;
    private static final double START_RADIUS = 1.5;
    private static final double MAX_RADIUS = 10.0;
    private static final double BAND = 1.0;
    private static final double JUMP_CLEARANCE = 0.6;
    private static final int TOTAL_TICKS = WINDUP_TICKS + RING_TICKS + 8;

    private final TheCorruptedBossEntity boss;
    private final Set<UUID> struck = new HashSet<>();
    private int tick;
    private double cx;
    private double cy;
    private double cz;

    public ShockwaveRingGoal(TheCorruptedBossEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.boss.getTarget();
        return target != null && target.isAlive()
                && this.boss.isAttackReady(TheCorruptedBossEntity.Attack.SHOCKWAVE)
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
        this.boss.beginAttack(TheCorruptedBossEntity.ANIM_SHOCKWAVE);
        this.boss.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.boss.finishAttack(34);
    }

    @Override
    public void tick() {
        if (this.tick >= TOTAL_TICKS) {
            return;
        }
        this.tick++;
        this.boss.getNavigation().stop();
        LivingEntity target = this.boss.getTarget();
        if (target != null) {
            Vec3 aim = BossCombat.aim(this.boss, target);
            BossCombat.faceDirection(this.boss, aim.x, aim.z);
        }
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        this.cx = this.boss.getX();
        this.cy = this.boss.getY();
        this.cz = this.boss.getZ();

        if (this.tick <= WINDUP_TICKS) {
            this.windup(level);
        } else if (this.tick - WINDUP_TICKS <= RING_TICKS) {
            if (this.tick == WINDUP_TICKS + 1) {
                BossCombat.fx(this.boss, 1, new Vec3(this.cx, this.cy, this.cz), Vec3.ZERO);
            }
            this.ring(level, this.tick - WINDUP_TICKS);
        }
    }

    private void windup(ServerLevel level) {
        if (this.tick == 1) {
            BossCombat.sound(this.boss, SoundEvents.ENDER_DRAGON_GROWL, 1.0F, 1.4F);
        }
        if (this.tick == WINDUP_TICKS - 3) {
            this.boss.swing(InteractionHand.MAIN_HAND);
        }
        // red energy gathers toward his feet
        double gather = 3.0 * (1.0 - this.tick / (double) WINDUP_TICKS) + 0.5;
        for (int i = 0; i < 6; i++) {
            double angle = this.tick * 0.6 + i * (Mth.TWO_PI / 6.0);
            double x = this.cx + Math.cos(angle) * gather;
            double z = this.cz + Math.sin(angle) * gather;
            double y = BossCombat.surfaceY(level, x, this.cy, z);
            level.sendParticles(BossCombat.CRIMSON, x, y + 0.2, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // faint outline of the area the ring will reach
        if (this.tick % 5 == 0) {
            for (int i = 0; i < 48; i++) {
                double angle = i * Mth.TWO_PI / 48.0;
                double x = this.cx + Math.cos(angle) * MAX_RADIUS;
                double z = this.cz + Math.sin(angle) * MAX_RADIUS;
                double y = BossCombat.surfaceY(level, x, this.cy, z);
                level.sendParticles(ParticleTypes.SMOKE, x, y + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (this.tick == WINDUP_TICKS) {
            this.boss.swing(InteractionHand.OFF_HAND);
            BossCombat.sound(this.boss, SoundEvents.WARDEN_ATTACK_IMPACT, 1.5F, 0.7F);
            BossCombat.sound(this.boss, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.3F);
            BossCombat.redBolt(level, this.cx, this.cy, this.cz, 3.5);
        }
    }

    private void ring(ServerLevel level, int step) {
        double radius = START_RADIUS + (MAX_RADIUS - START_RADIUS) * (step - 1) / (RING_TICKS - 1);

        int points = Math.max(24, (int) (radius * 7.0));
        for (int i = 0; i < points; i++) {
            double angle = i * Mth.TWO_PI / points;
            double x = this.cx + Math.cos(angle) * radius;
            double z = this.cz + Math.sin(angle) * radius;
            double y = BossCombat.surfaceY(level, x, this.cy, z);
            level.sendParticles(BossCombat.CRIMSON, x, y + 0.15, z, 1, 0.1, 0.05, 0.1, 0.0);
            if (i % 4 == 0) {
                BossCombat.redBolt(level, x, y, z, 1.4);
            }
        }
        if (step % 4 == 1) {
            BossCombat.sound(level, new Vec3(this.cx, this.cy, this.cz), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 1.5F);
        }

        for (LivingEntity victim : BossCombat.victimsIn(this.boss,
                this.boss.getBoundingBox().inflate(MAX_RADIUS + 2.0, 4.0, MAX_RADIUS + 2.0))) {
            if (this.struck.contains(victim.getUUID())) {
                continue;
            }
            double dist = Math.sqrt((victim.getX() - this.cx) * (victim.getX() - this.cx)
                    + (victim.getZ() - this.cz) * (victim.getZ() - this.cz));
            if (Math.abs(dist - radius) > BAND) {
                continue;
            }
            if (BossCombat.heightAboveGround(victim) > JUMP_CLEARANCE) {
                continue; // jumped over it
            }
            this.struck.add(victim.getUUID());
            if (BossCombat.hit(this.boss, victim, DAMAGE, 0.0)) {
                BossCombat.pushAway(this.boss, victim, 1.1, 0.35);
                level.sendParticles(BossCombat.CRIMSON, victim.getX(), victim.getY() + 0.5, victim.getZ(), 14, 0.4, 0.5, 0.4, 0.05);
            }
        }
    }
}
