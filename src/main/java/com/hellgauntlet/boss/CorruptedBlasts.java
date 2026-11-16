package com.hellgauntlet.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The boss's version of the Void Gauntlet blast: a sphere that grows to full size in about 4 ticks and deletes
 * everything new inside it. Entities vanish without loot, blocks vanish without drops, bedrock-type blocks survive.
 * Mobs with 150+ max health (and the ender dragon) are killed properly instead of deleted.
 *
 * Differences from the gauntlet version: players, dropped items and XP orbs are never erased (so the blast can
 * not wipe the boss's own death loot or the person who shot the orb), and the sphere visual is drawn with
 * particles, so no extra entity or renderer is needed.
 */
public final class CorruptedBlasts {
    public static final float KILL_HEALTH = 150.0F;

    private static final List<Blast> BLASTS = new ArrayList<>();

    private CorruptedBlasts() {}

    private static final class Blast {
        final ServerLevel level;
        final Vec3 center;
        final UUID owner;
        final int maxRadius;
        final boolean deleteBlocks;
        final int step;
        int radius = -1; // last shell that was processed

        Blast(ServerLevel level, Vec3 center, UUID owner, int maxRadius, boolean deleteBlocks) {
            this.level = level;
            this.center = center;
            this.owner = owner;
            this.maxRadius = maxRadius;
            this.deleteBlocks = deleteBlocks;
            this.step = Math.max(1, (maxRadius + 3) / 4);
        }
    }

    public static void start(ServerLevel level, Vec3 center, UUID owner, int radius, boolean deleteBlocks) {
        BLASTS.add(new Blast(level, center, owner, radius, deleteBlocks));
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 1.5F, 0.5F + 0.1F * radius);
    }

    public static void clear() {
        BLASTS.clear();
    }

    public static boolean canErase(Entity e, UUID owner) {
        return !(e instanceof TheCorruptedBossEntity)
                && !(e instanceof Player)
                && !(e instanceof ItemEntity)
                && !(e instanceof ExperienceOrb)
                && !(e instanceof EnderDragonPart)
                && !e.getUUID().equals(owner);
    }

    private static void erase(ServerLevel level, Entity e) {
        if (e instanceof EnderDragon dragon) {
            if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.DYING) {
                dragon.getPhaseManager().setPhase(EnderDragonPhase.DYING);
            }
        } else if (e instanceof LivingEntity mob && mob.getMaxHealth() >= KILL_HEALTH) {
            killWithLoot(level, mob);
        } else {
            e.discard();
        }
    }

    private static void killWithLoot(ServerLevel level, LivingEntity mob) {
        if (mob.isDeadOrDying()) {
            return; // the blast touches it every tick, don't kill it twice
        }
        DamageSource source = level.damageSources().genericKill();
        mob.setInvulnerable(false);
        mob.invulnerableTime = 0;
        mob.hurt(source, Float.MAX_VALUE);
        if (mob.isAlive()) {
            mob.setHealth(0.0F);
            mob.die(source);
        }
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Iterator<Blast> it = BLASTS.iterator();
        while (it.hasNext()) {
            Blast blast = it.next();
            if (blast.level != level) {
                continue;
            }
            tickBlast(blast);
            if (blast.radius >= blast.maxRadius) {
                it.remove();
            }
        }
    }

    private static double distSqToBox(AABB bb, Vec3 p) {
        double x = Mth.clamp(p.x, bb.minX, bb.maxX) - p.x;
        double y = Mth.clamp(p.y, bb.minY, bb.maxY) - p.y;
        double z = Mth.clamp(p.z, bb.minZ, bb.maxZ) - p.z;
        return x * x + y * y + z * z;
    }

    private static void tickBlast(Blast blast) {
        ServerLevel level = blast.level;
        final Vec3 c = blast.center;
        final int from = blast.radius + 1;
        final int to = Math.min(blast.maxRadius, blast.radius + blast.step);
        final double reach = to + 0.5;

        // Entities touching the sphere, judged by hitbox
        AABB area = new AABB(c, c).inflate(reach + 8.0);
        List<Entity> targets = level.getEntities((Entity) null, area, e -> {
            if (!canErase(e, blast.owner)) {
                return false;
            }
            AABB bb = e.getBoundingBox();
            if (e instanceof EnderDragon) {
                bb = bb.inflate(4.0);
            }
            return distSqToBox(bb, c) <= reach * reach;
        });
        for (Entity e : targets) {
            erase(level, e);
        }

        // Blocks on the new shells of the sphere
        if (blast.deleteBlocks) {
            BlockPos origin = BlockPos.containing(c);
            double outer = reach * reach;
            double inner = from == 0 ? -1.0 : (from - 0.5) * (from - 0.5);
            for (int dx = -to; dx <= to; dx++) {
                for (int dy = -to; dy <= to; dy++) {
                    for (int dz = -to; dz <= to; dz++) {
                        double d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 > outer || d2 <= inner) {
                            continue;
                        }
                        BlockPos pos = origin.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                            continue; // air and unbreakable blocks like bedrock
                        }
                        if (level.getBlockEntity(pos) != null) {
                            level.removeBlockEntity(pos); // so chests don't drop their contents
                        }
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }

        // Visual: the black sphere with a crimson glow, drawn on the newest shell
        BossCombat.sphereShell(level, c, to + 0.5, Math.min(260, 24 + to * to * 6));

        blast.radius = to;
    }
}
