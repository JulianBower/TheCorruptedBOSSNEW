package com.hellgauntlet.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The Corrupted. Immune to every direct hit; the only way to hurt him is to shoot the Void Orb he charges
 * above his raised right hand so it detonates in his hand. Six detonations (50 HP each) kill him.
 */
public class TheCorruptedBossEntity extends Monster {
    public static final float MAX_HEALTH = 300.0F;
    public static final int MAX_ORB_HITS = 6;
    public static final int PHASE_TWO_HITS = 3;
    public static final float OVERLOAD_DAMAGE = 50.0F;
    public static final int STUN_TICKS = 60;

    public static final int ORB_INTERVAL_TICKS = 700;       // ~35 seconds between orb charges
    public static final int ORB_CHARGE_TICKS = 45;
    public static final int ORB_CHARGE_TICKS_PHASE_TWO = 35;
    public static final double KILL_WAVE_RADIUS = 35.0;
    public static final int OVERLOAD_BLAST_RADIUS = 6;

    // Orb sits above the raised right hand (right of the body, above the head)
    public static final double ORB_SIDE_OFFSET = 0.58;
    public static final double ORB_HEIGHT_OFFSET = 0.7;
    public static final double ORB_HIT_RADIUS = 1.3;

    /** Values of the synced ANIMATION field, for the renderer to use later. */
    public static final int ANIM_IDLE = 0;
    public static final int ANIM_COMBO = 1;
    public static final int ANIM_RUPTURE = 2;
    public static final int ANIM_SHOCKWAVE = 3;
    public static final int ANIM_DASH = 4;
    public static final int ANIM_ORB_CHARGE = 5;
    public static final int ANIM_STUNNED = 6;

    /** Attack rotation order. DASH only appears in phase 2. */
    public enum Attack { COMBO, RUPTURE, SHOCKWAVE, DASH }

    private static final EntityDataAccessor<Integer> ORB_HITS =
            SynchedEntityData.defineId(TheCorruptedBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_CHARGING =
            SynchedEntityData.defineId(TheCorruptedBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CHARGE_TICKS =
            SynchedEntityData.defineId(TheCorruptedBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIMATION =
            SynchedEntityData.defineId(TheCorruptedBossEntity.class, EntityDataSerializers.INT);

    private static final ResourceLocation PHASE_TWO_SPEED =
            ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "phase_two_speed");

    private static final int INITIAL_ORB_DELAY = 400;
    private static final int ABORTED_ORB_DELAY = 400;
    private static final int QUEUE_STALL_LIMIT = 100;
    private static final int CLANG_COOLDOWN_TICKS = 4;
    private static final int BURST_TICKS = 12;
    private static final double BURST_RADIUS = 14.0;
    private static final int KILL_WAVE_TICKS = 10;
    private static final int DEATH_TICKS = 80;
    private static final int DEATH_XP = 3000;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);

    private Attack queuedAttack = Attack.COMBO;
    private boolean attacking;
    private int attackDelay = 40;
    private int queueStall;
    private int orbCooldown = INITIAL_ORB_DELAY;
    private int stunTicks;
    private int burstTicks;
    private int waveTicks;
    private Vec3 waveCenter = Vec3.ZERO;
    private int lastClangTick = -100;

    public TheCorruptedBossEntity(EntityType<? extends TheCorruptedBossEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0; // XP is awarded manually on death, whoever lands the last detonation
        this.setPersistenceRequired();
        this.setPathfindingMalus(PathType.LAVA, 0.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.95)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new VoidOrbChargeGoal(this));
        this.goalSelector.addGoal(2, new ShadowDashGoal(this));
        this.goalSelector.addGoal(3, new ShockwaveRingGoal(this));
        this.goalSelector.addGoal(3, new VoidRuptureGoal(this));
        this.goalSelector.addGoal(3, new HeavyComboGoal(this));
        this.goalSelector.addGoal(5, new ChaseTargetGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ORB_HITS, 0);
        builder.define(IS_CHARGING, false);
        builder.define(CHARGE_TICKS, 0);
        builder.define(ANIMATION, ANIM_IDLE);
    }

    // ------------------------------------------------------------------ synced state

    public int getOrbHits() {
        return this.entityData.get(ORB_HITS);
    }

    private void setOrbHits(int hits) {
        this.entityData.set(ORB_HITS, Mth.clamp(hits, 0, MAX_ORB_HITS));
    }

    public boolean isCharging() {
        return this.entityData.get(IS_CHARGING);
    }

    private void setCharging(boolean charging) {
        this.entityData.set(IS_CHARGING, charging);
    }

    public int getChargeTicks() {
        return this.entityData.get(CHARGE_TICKS);
    }

    private void setChargeTicks(int ticks) {
        this.entityData.set(CHARGE_TICKS, ticks);
    }

    public int getAnimation() {
        return this.entityData.get(ANIMATION);
    }

    private void setAnimation(int animation) {
        this.entityData.set(ANIMATION, animation);
    }

    public boolean isPhaseTwo() {
        return this.getOrbHits() >= PHASE_TWO_HITS;
    }

    public boolean isStunned() {
        return this.getAnimation() == ANIM_STUNNED;
    }

    public boolean isAttacking() {
        return this.attacking;
    }

    public int getChargeDuration() {
        return this.isPhaseTwo() ? ORB_CHARGE_TICKS_PHASE_TWO : ORB_CHARGE_TICKS;
    }

    /** 0..1 while charging, for the renderer. */
    public float getChargeProgress() {
        return Mth.clamp(this.getChargeTicks() / (float) this.getChargeDuration(), 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------ immunity

    /** Nothing damages him except commands and the void. Everything else clangs off. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        boolean deliberate = source.getEntity() != null || source.getDirectEntity() != null
                || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_PROJECTILE);
        if (deliberate && !this.level().isClientSide && this.tickCount - this.lastClangTick >= CLANG_COOLDOWN_TICKS) {
            this.lastClangTick = this.tickCount;
            BossCombat.sound(this, SoundEvents.SHIELD_BLOCK, 1.0F, 0.7F + this.random.nextFloat() * 0.3F);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + this.getBbHeight() * 0.6,
                        this.getZ(), 6, 0.4, 0.5, 0.4, 0.1);
            }
        }
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ------------------------------------------------------------------ boss bar

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    // ------------------------------------------------------------------ per-tick server logic

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));

        if (this.attackDelay > 0) {
            this.attackDelay--;
        }
        if (this.orbCooldown > 0 && !this.isCharging() && this.getTarget() != null) {
            this.orbCooldown--;
        }
        if (this.stunTicks > 0) {
            this.tickStun();
        } else if (this.getTarget() != null && !this.attacking && !this.isCharging()
                && this.attackDelay <= 0 && ++this.queueStall > QUEUE_STALL_LIMIT) {
            // the queued attack never became possible (out of range, no line of sight): move on
            this.advanceRotation();
            this.queueStall = 0;
        }
        this.tickBurst();
        this.tickKillWave();
    }

    private void tickStun() {
        this.stunTicks--;
        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.tickCount % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + this.getBbHeight() * 0.9,
                        this.getZ(), 3, 0.4, 0.5, 0.4, 0.01);
                serverLevel.sendParticles(BossCombat.CRIMSON, this.getX(), this.getY() + this.getBbHeight() * 0.6,
                        this.getZ(), 4, 0.5, 0.8, 0.5, 0.0);
            }
            if (this.stunTicks % 15 == 0) {
                BossCombat.redBolt(serverLevel, this.getX() + (this.random.nextDouble() - 0.5) * 2.0, this.getY(),
                        this.getZ() + (this.random.nextDouble() - 0.5) * 2.0, 3.0);
            }
        }
        if (this.stunTicks <= 0) {
            this.setAnimation(ANIM_IDLE);
        }
    }

    private void tickBurst() {
        if (this.burstTicks <= 0) {
            return;
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            int step = BURST_TICKS - this.burstTicks + 1;
            double radius = step * BURST_RADIUS / BURST_TICKS;
            int points = (int) (radius * 8.0) + 12;
            for (int i = 0; i < points; i++) {
                double angle = i * Mth.TWO_PI / points;
                double x = this.getX() + Math.cos(angle) * radius;
                double z = this.getZ() + Math.sin(angle) * radius;
                double y = BossCombat.surfaceY(serverLevel, x, this.getY(), z);
                serverLevel.sendParticles(BossCombat.CRIMSON, x, y + 0.3 + this.random.nextDouble() * 0.8, z, 1, 0.0, 0.0, 0.0, 0.0);
                if (i % 6 == 0) {
                    BossCombat.redBolt(serverLevel, x, y, z, 2.0);
                }
            }
        }
        this.burstTicks--;
    }

    private void tickKillWave() {
        if (this.waveTicks <= 0) {
            return;
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            int step = KILL_WAVE_TICKS - this.waveTicks + 1;
            double radius = KILL_WAVE_RADIUS * step / KILL_WAVE_TICKS;
            BossCombat.sphereShell(serverLevel, this.waveCenter, radius, 180);
        }
        this.waveTicks--;
    }

    // ------------------------------------------------------------------ attack rotation

    public boolean isAttackReady(Attack attack) {
        return !this.attacking && !this.isCharging() && !this.isStunned()
                && this.attackDelay <= 0 && this.queuedAttack == attack;
    }

    public void beginAttack(int animation) {
        this.attacking = true;
        this.setAnimation(animation);
    }

    public void finishAttack(int recoveryTicks) {
        this.attacking = false;
        this.attackDelay = Math.max(this.attackDelay, recoveryTicks);
        this.queueStall = 0;
        this.advanceRotation();
        if (!this.isStunned() && !this.isCharging()) {
            this.setAnimation(ANIM_IDLE);
        }
    }

    private void advanceRotation() {
        Attack[] all = Attack.values();
        int index = this.queuedAttack.ordinal();
        for (int i = 0; i < all.length; i++) {
            index = (index + 1) % all.length;
            if (all[index] != Attack.DASH || this.isPhaseTwo()) {
                break;
            }
        }
        this.queuedAttack = all[index];
    }

    // ------------------------------------------------------------------ void orb

    public boolean isOrbReady() {
        return this.orbCooldown <= 0 && !this.isCharging() && !this.isStunned();
    }

    /** Center of the charging orb: above the head, over the raised right hand. */
    public Vec3 orbPosition() {
        double yaw = Math.toRadians(this.yBodyRot);
        return new Vec3(
                this.getX() - Math.cos(yaw) * ORB_SIDE_OFFSET,
                this.getY() + this.getBbHeight() + ORB_HEIGHT_OFFSET,
                this.getZ() - Math.sin(yaw) * ORB_SIDE_OFFSET);
    }

    public void beginOrbCharge() {
        this.setCharging(true);
        this.setChargeTicks(0);
        this.setAnimation(ANIM_ORB_CHARGE);
        this.getNavigation().stop();
        BossCombat.sound(this, SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 0.6F);
        BossCombat.sound(this, SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.8F);
    }

    /** One tick of charging: grows the orb visual, pulses sound and lightning. */
    public void advanceCharge() {
        int ticks = this.getChargeTicks() + 1;
        this.setChargeTicks(ticks);
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        float progress = this.getChargeProgress();
        Vec3 orb = this.orbPosition();
        double radius = 0.35 + 0.95 * progress;

        serverLevel.sendParticles(BossCombat.SHADOW, orb.x, orb.y, orb.z, 6, radius * 0.4, radius * 0.4, radius * 0.4, 0.0);
        for (int i = 0; i < 3; i++) {
            double angle = ticks * 0.55 + i * (Mth.TWO_PI / 3.0);
            serverLevel.sendParticles(BossCombat.CRIMSON,
                    orb.x + Math.cos(angle) * radius,
                    orb.y + Math.sin(ticks * 0.3 + i) * radius * 0.6,
                    orb.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
        int boltEvery = progress > 0.75F ? 3 : 6;
        if (ticks % boltEvery == 0) {
            Vec3 hand = orb.subtract(0.0, ORB_HEIGHT_OFFSET - 0.2, 0.0);
            BossCombat.boltBetween(serverLevel, orb, hand);
        }
        if (ticks % 10 == 0) {
            BossCombat.sound(this, SoundEvents.BEACON_AMBIENT, 2.0F, 0.5F + progress);
        }
    }

    /** First arrow fired by a player that crosses the orb this tick, if any. */
    @Nullable
    public AbstractArrow findOrbArrow() {
        Vec3 orb = this.orbPosition();
        AABB box = new AABB(orb, orb).inflate(ORB_HIT_RADIUS + 4.0);
        for (AbstractArrow arrow : this.level().getEntitiesOfClass(AbstractArrow.class, box)) {
            if (!(arrow instanceof Arrow || arrow instanceof SpectralArrow) || !(arrow.getOwner() instanceof Player)) {
                continue;
            }
            Vec3 from = new Vec3(arrow.xo, arrow.yo, arrow.zo);
            Vec3 to = arrow.position();
            if (from.distanceToSqr(to) < 1.0E-4) {
                continue; // stuck in a block or standing still
            }
            if (segmentDistanceSqr(orb, from, to) <= ORB_HIT_RADIUS * ORB_HIT_RADIUS) {
                return arrow;
            }
        }
        return null;
    }

    private static double segmentDistanceSqr(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 < 1.0E-9 ? 0.0 : Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
        return a.add(ab.scale(t)).distanceToSqr(p);
    }

    /** The orb was shot: it detonates in his hand, hurts him for 50, stuns him, and counts toward his defeat. */
    public void overloadOrb() {
        if (!(this.level() instanceof ServerLevel serverLevel) || !this.isCharging() || this.isDeadOrDying()) {
            return;
        }
        Vec3 orb = this.orbPosition();
        this.setCharging(false);
        this.setChargeTicks(0);
        this.orbCooldown = ORB_INTERVAL_TICKS;

        CorruptedBlasts.start(serverLevel, orb, this.getUUID(), OVERLOAD_BLAST_RADIUS, true);
        BossCombat.sound(this, SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 0.7F);
        BossCombat.sound(this, SoundEvents.WARDEN_ATTACK_IMPACT, 2.0F, 0.5F);
        BossCombat.boltBetween(serverLevel, orb, this.position());

        int hits = Math.min(MAX_ORB_HITS, this.getOrbHits() + 1);
        this.setOrbHits(hits);

        float remaining = this.getHealth() - OVERLOAD_DAMAGE;
        if (remaining <= 0.0F || hits >= MAX_ORB_HITS) {
            this.setHealth(0.0F);
            this.die(this.damageSources().genericKill());
            return;
        }
        this.setHealth(remaining);
        this.stun(STUN_TICKS);
        this.attackDelay = Math.max(this.attackDelay, STUN_TICKS + 20);
        if (hits == PHASE_TWO_HITS) {
            this.enterPhaseTwo();
        }
    }

    /** The charge finished: a shockwave that kills every survival player within 35 blocks. */
    public void releaseKillWave() {
        this.setCharging(false);
        this.setChargeTicks(0);
        this.setAnimation(ANIM_IDLE);
        this.orbCooldown = ORB_INTERVAL_TICKS;
        this.attackDelay = Math.max(this.attackDelay, 50);
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 orb = this.orbPosition();
        this.waveCenter = orb;
        this.waveTicks = KILL_WAVE_TICKS;

        BossCombat.sound(this, SoundEvents.ENDER_DRAGON_GROWL, 4.0F, 0.5F);
        BossCombat.sound(this, SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0F, 0.5F);
        BossCombat.sound(this, SoundEvents.WITHER_BREAK_BLOCK, 4.0F, 0.5F);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, orb.x, orb.y, orb.z, 3, 1.0, 1.0, 1.0, 0.0);

        double r2 = KILL_WAVE_RADIUS * KILL_WAVE_RADIUS;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive() || player.distanceToSqr(this) > r2) {
                continue;
            }
            player.invulnerableTime = 0;
            player.hurt(this.damageSources().genericKill(), Float.MAX_VALUE);
        }
        LivingEntity target = this.getTarget();
        if (target != null && !(target instanceof Player) && target.isAlive() && target.distanceToSqr(this) <= r2) {
            target.invulnerableTime = 0;
            target.hurt(this.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    /** The orb goal ended without the orb finishing or being shot (target lost or died). */
    public void abortOrbCharge() {
        if (!this.isCharging()) {
            return;
        }
        this.setCharging(false);
        this.setChargeTicks(0);
        this.orbCooldown = ABORTED_ORB_DELAY;
        if (!this.isStunned()) {
            this.setAnimation(ANIM_IDLE);
        }
    }

    private void stun(int ticks) {
        this.stunTicks = ticks;
        this.setAnimation(ANIM_STUNNED);
        this.getNavigation().stop();
    }

    // ------------------------------------------------------------------ phase two

    private void applyPhaseTwoModifiers() {
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrReplacePermanentModifier(
                    new AttributeModifier(PHASE_TWO_SPEED, 0.05, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Third detonation: the corrupted armor awakens in a crimson shockwave. */
    private void enterPhaseTwo() {
        this.applyPhaseTwoModifiers();
        BossCombat.sound(this, SoundEvents.WITHER_SPAWN, 4.0F, 0.75F);
        BossCombat.sound(this, SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 0.6F);
        this.burstTicks = BURST_TICKS;
        BossCombat.fx(this, 4, this.position(), Vec3.ZERO);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1.5, this.getZ(),
                    2, 1.0, 1.0, 1.0, 0.0);
            for (ServerPlayer player : serverLevel.players()) {
                if (player.isCreative() || player.isSpectator()
                        || player.distanceToSqr(this) > BURST_RADIUS * BURST_RADIUS) {
                    continue;
                }
                BossCombat.pushAway(this, player, 2.2, 0.55);
            }
        }
    }

    // ------------------------------------------------------------------ death

    @Override
    public void die(DamageSource source) {
        boolean wasDead = this.dead;
        super.die(source);
        if (!wasDead && this.dead) {
            this.setCharging(false);
            this.setChargeTicks(0);
            this.bossEvent.setProgress(0.0F);
            if (this.level() instanceof ServerLevel serverLevel) {
                ExperienceOrb.award(serverLevel, this.position(), DEATH_XP);
                // always exactly one core, dropped when the boss is defeated (6th orb explosion)
                this.spawnAtLocation(new net.minecraft.world.item.ItemStack(ModItems.CORRUPTED_CORE.get()));
                // 4-7 steel ingots alongside the core
                this.spawnAtLocation(new net.minecraft.world.item.ItemStack(ModItems.STEEL_INGOT.get(), 4 + this.random.nextInt(4)));
            }
        }
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.deathTime % 4 == 0) {
            double x = this.getX() + (this.random.nextDouble() - 0.5) * 2.0;
            double z = this.getZ() + (this.random.nextDouble() - 0.5) * 2.0;
            BossCombat.redBolt(serverLevel, x, this.getY(), z, 3.0 + this.random.nextDouble() * 2.0);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, this.getY() + 1.5, z, 4, 0.4, 0.8, 0.4, 0.02);
        }
        if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
            Vec3 center = this.position().add(0.0, 1.5, 0.0);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 3, 1.0, 1.0, 1.0, 0.0);
            BossCombat.sphereShell(serverLevel, center, 4.0, 220);
            BossCombat.sound(this, SoundEvents.WITHER_BREAK_BLOCK, 3.0F, 0.6F);
            serverLevel.broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_AMBIENT;
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    // ------------------------------------------------------------------ save data

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("OrbHits", this.getOrbHits());
        tag.putInt("OrbCooldown", this.orbCooldown);
        tag.putInt("StunTicks", this.stunTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setOrbHits(tag.getInt("OrbHits"));
        this.orbCooldown = tag.contains("OrbCooldown") ? tag.getInt("OrbCooldown") : INITIAL_ORB_DELAY;
        this.stunTicks = tag.getInt("StunTicks");
        if (this.stunTicks > 0) {
            this.setAnimation(ANIM_STUNNED);
        }
        if (this.isPhaseTwo()) {
            this.applyPhaseTwoModifiers();
        }
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }
}
