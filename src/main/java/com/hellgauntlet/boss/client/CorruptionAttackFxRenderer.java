package com.hellgauntlet.boss.client;

import com.hellgauntlet.boss.ClientFxState;
import com.hellgauntlet.boss.TheCorruptedMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/** Draws the 3D red lightning of the boss attacks (ring, rupture line, dash / slam, awakening burst). */
@EventBusSubscriber(modid = TheCorruptedMod.MODID, value = Dist.CLIENT)
public final class CorruptionAttackFxRenderer {
    private static final ResourceLocation BOLT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "textures/effect/corruption_bolt.png");

    private CorruptionAttackFxRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            ClientFxState.ATTACKS.clear();
            return;
        }
        long now = level.getGameTime();
        ClientFxState.ATTACKS.removeIf(a -> now - a.start() > ClientFxState.ATTACK_LIFE_TICKS);
        if (ClientFxState.ATTACKS.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(BOLT_TEXTURE);
        VertexConsumer vc = buffers.getBuffer(type);

        for (ClientFxState.Attack a : ClientFxState.ATTACKS) {
            float age = (now - a.start()) + partial;
            int tick = (int) (now - a.start());
            ps.pushPose();
            ps.translate(a.origin().x - cam.x, a.origin().y - cam.y, a.origin().z - cam.z);
            PoseStack.Pose pose = ps.last();
            RandomSource seedRnd = RandomSource.create(a.seed() * 31L + tick * 7919L);
            switch (a.type()) {
                case 1 -> ring(vc, pose, seedRnd, age);
                case 2 -> line(vc, pose, seedRnd, a.dir(), age);
                case 3 -> dash(vc, pose, seedRnd, a.dir(), age);
                case 4 -> burst(vc, pose, seedRnd, age);
                default -> {
                }
            }
            ps.popPose();
        }
        buffers.endBatch(type);
    }

    private static float fade(float age, float fullUntil) {
        return age <= fullUntil ? 1.0F : Math.max(0.0F, (ClientFxState.ATTACK_LIFE_TICKS - age) / (ClientFxState.ATTACK_LIFE_TICKS - fullUntil));
    }

    /** Ground-level ring expanding from 1 to 10 blocks over 15 ticks. */
    private static void ring(VertexConsumer vc, PoseStack.Pose pose, RandomSource rnd, float age) {
        double radius = 1.0 + 9.0 * Math.min(age / 15.0F, 1.0F);
        float fade = fade(age, 15.0F);
        int n = 14 + (int) (radius * 2.5);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2.0 * i / n;
            double a1 = Math.PI * 2.0 * (i + 1) / n;
            Vec3 p0 = new Vec3(Math.cos(a0) * radius, 0.08, Math.sin(a0) * radius);
            Vec3 p1 = new Vec3(Math.cos(a1) * radius, 0.08, Math.sin(a1) * radius);
            BossVoidFx.strike(vc, pose, BossVoidFx.jaggedPath(rnd, p0, p1, 3, 0.22), 1.0F, fade);
            Vec3 top = p0.add(Math.cos(a0) * (rnd.nextDouble() - 0.3), 0.5 + rnd.nextDouble() * 1.1, Math.sin(a0) * (rnd.nextDouble() - 0.3));
            BossVoidFx.strike(vc, pose, BossVoidFx.jaggedPath(rnd, p0, top, 4, 0.2), 0.8F, fade);
        }
    }

    /** 16-block line of erupting vertical and branching bolts racing forward along `dir`. */
    private static void line(VertexConsumer vc, PoseStack.Pose pose, RandomSource rnd, Vec3 dir, float age) {
        Vec3 d = new Vec3(dir.x, 0.0, dir.z);
        d = d.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : d.normalize();
        double head = 16.0 * Math.min(age / 10.0F, 1.0F);
        float fade = fade(age, 12.0F);
        for (double dist = 0.0; dist <= head; dist += 1.3) {
            Vec3 base = d.scale(dist).add(0.0, 0.08, 0.0);
            Vec3 top = base.add((rnd.nextDouble() - 0.5) * 0.8, 2.0 + rnd.nextDouble() * 2.4, (rnd.nextDouble() - 0.5) * 0.8);
            List<Vec3> path = BossVoidFx.jaggedPath(rnd, base, top, 5, 0.16);
            BossVoidFx.strike(vc, pose, path, 1.0F, fade);
            if (rnd.nextFloat() < 0.6F) {
                Vec3 from = path.get(2 + rnd.nextInt(2));
                Vec3 end = from.add(BossVoidFx.randomUnit(rnd).scale(0.9 + rnd.nextDouble() * 0.9));
                BossVoidFx.strike(vc, pose, BossVoidFx.jaggedPath(rnd, from, end, 3, 0.2), 0.6F, fade);
            }
            BossVoidFx.strike(vc, pose, BossVoidFx.jaggedPath(rnd, base, base.add(d.scale(1.3)), 3, 0.22), 0.8F, fade);
        }
    }

    /** Dash: streaks along the path (direction = full displacement). Impact: short burst (tiny direction). */
    private static void dash(VertexConsumer vc, PoseStack.Pose pose, RandomSource rnd, Vec3 dir, float age) {
        float fade = fade(age, 6.0F);
        if (dir.length() > 0.5) {
            Vec3 side = dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
            for (int i = 0; i < 6; i++) {
                double off = (rnd.nextDouble() - 0.5) * 1.6;
                double h = 0.3 + rnd.nextDouble() * 2.2;
                Vec3 a = side.scale(off).add(0.0, h, 0.0);
                Vec3 b = a.add(dir.scale(0.4 + 0.6 * Math.min(age / 5.0F, 1.0F)));
                BossVoidFx.strike(vc, pose, BossVoidFx.jaggedPath(rnd, a, b, 9, 0.07), 1.0F, fade);
            }
        } else {
            for (int i = 0; i < 12; i++) {
                Vec3 dirUnit = BossVoidFx.randomUnit(rnd);
                Vec3 end = dirUnit.scale((1.2 + rnd.nextDouble() * 1.8) * Math.min(1.0F, 0.4F + age / 4.0F));
                BossVoidFx.strike(vc, pose, BossVoidFx.jaggedPath(rnd, Vec3.ZERO, end, 4, 0.2), 1.0F, fade);
            }
        }
    }

    /** Phase 2 awakening: a dome of bolts bursting outward plus a ground ring. */
    private static void burst(VertexConsumer vc, PoseStack.Pose pose, RandomSource rnd, float age) {
        double radius = 1.5 + 7.5 * Math.min(age / 10.0F, 1.0F);
        float fade = fade(age, 10.0F);
        Vec3 center = new Vec3(0.0, 1.4, 0.0);
        for (int i = 0; i < 30; i++) {
            Vec3 d = BossVoidFx.randomUnit(rnd);
            d = new Vec3(d.x, Math.abs(d.y) * 0.9 - 0.1, d.z).normalize();
            BossVoidFx.strike(vc, pose, BossVoidFx.jaggedPath(rnd, center, center.add(d.scale(radius)), 7, 0.14), 1.2F, fade);
        }
        for (int i = 0; i < 16; i++) {
            double a0 = Math.PI * 2.0 * i / 16;
            double a1 = Math.PI * 2.0 * (i + 1) / 16;
            Vec3 p0 = new Vec3(Math.cos(a0) * radius, 0.1, Math.sin(a0) * radius);
            Vec3 p1 = new Vec3(Math.cos(a1) * radius, 0.1, Math.sin(a1) * radius);
            BossVoidFx.strike(vc, pose, BossVoidFx.jaggedPath(rnd, p0, p1, 3, 0.2), 1.0F, fade);
        }
    }
}
