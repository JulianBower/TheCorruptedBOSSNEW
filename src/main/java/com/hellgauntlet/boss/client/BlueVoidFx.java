package com.hellgauntlet.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Electric blue version of the void effects: #38BDF8 sky-blue glow with a #BAE6FD white-cyan core. Used by the Fused
 * Blue-Lightning Gauntlet (orb, laser, blast) and by Sovereign armor lightning. Everything is additive
 * (RenderType.lightning) except the black core sphere, which comes from BossVoidFx.sphere.
 */
public final class BlueVoidFx {
    private static final int[] GLOW = {56, 189, 248};
    private static final int[] CORE = {186, 230, 253};

    private BlueVoidFx() {}

    /** A jagged bolt: wide sky-blue glow with a thin bright core. `fade` is 0..1. */
    public static void strike(VertexConsumer vc, PoseStack.Pose pose, List<Vec3> pts, float scale, float fade) {
        int a = (int) (Math.max(0.0F, Math.min(1.0F, fade)) * 255.0F);
        polyline(vc, pose, pts, 0.07F * scale, GLOW, (int) (a * 0.7F));
        polyline(vc, pose, pts, 0.025F * scale, CORE, a);
    }

    /** Ring facing the camera that hugs the sphere outline (pose already rotated to the camera). */
    public static void haloRing(VertexConsumer vc, PoseStack.Pose pose, float inner, float outer, int alpha) {
        int n = 32;
        for (int i = 0; i < n; i++) {
            double a0 = 2.0 * Math.PI * i / n;
            double a1 = 2.0 * Math.PI * (i + 1) / n;
            Vec3 i0 = new Vec3(Math.cos(a0) * inner, Math.sin(a0) * inner, 0.0);
            Vec3 i1 = new Vec3(Math.cos(a1) * inner, Math.sin(a1) * inner, 0.0);
            Vec3 o0 = new Vec3(Math.cos(a0) * outer, Math.sin(a0) * outer, 0.0);
            Vec3 o1 = new Vec3(Math.cos(a1) * outer, Math.sin(a1) * outer, 0.0);
            vertex(vc, pose, i0, GLOW, alpha);
            vertex(vc, pose, o0, GLOW, 0);
            vertex(vc, pose, o1, GLOW, 0);
            vertex(vc, pose, i1, GLOW, alpha);
            vertex(vc, pose, i1, GLOW, alpha);
            vertex(vc, pose, o1, GLOW, 0);
            vertex(vc, pose, o0, GLOW, 0);
            vertex(vc, pose, i0, GLOW, alpha);
        }
    }

    /**
     * Aggressive arcs around a sphere: a brand new shape EVERY tick, `count` arcs (about 1.8x the red orb's 4), a wide
     * angular sweep and sharp jitter.
     */
    public static void sphereArcs(VertexConsumer vc, PoseStack.Pose pose, float radius, int count, long seed, int tick, float width) {
        for (int arc = 0; arc < count; arc++) {
            RandomSource rnd = RandomSource.create(seed * 341873128712L + (long) tick * 132897987541L + arc * 7919L);
            Vec3 a = randomDir(rnd);
            Vec3 tangent = a.cross(randomDir(rnd)).normalize();
            double span = 1.3 + rnd.nextDouble() * 1.5;
            int segments = Math.max(7, Math.min(26, (int) (radius * span / 0.45)));
            double wobble = Math.min(radius * 0.32, 0.6);
            List<Vec3> pts = new ArrayList<>();
            for (int i = 0; i <= segments; i++) {
                double ang = ((double) i / segments - 0.5) * span;
                Vec3 dir = a.scale(Math.cos(ang)).add(tangent.scale(Math.sin(ang)));
                double r = radius * 1.08 + rnd.nextDouble() * Math.min(radius * 0.45, 0.8);
                Vec3 jitter = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).scale(wobble);
                pts.add(dir.scale(r).add(i == 0 || i == segments ? Vec3.ZERO : jitter));
            }
            strikeRaw(vc, pose, pts, width, 255);
        }
    }

    /**
     * High-velocity spiralling bolts racing down a beam between back and front, with crackling side forks. Every bolt
     * is a short helix window that slides along the beam several blocks per tick.
     */
    public static void beamSpirals(VertexConsumer vc, PoseStack.Pose pose, Vec3 dir, Vec3 u, Vec3 v, double back, double front,
                                   float beamRadius, int bolts, long seed, float age) {
        double len = front - back;
        if (len <= 0.5) {
            return;
        }
        double window = Math.min(len, 7.0);
        for (int b = 0; b < bolts; b++) {
            RandomSource rnd = RandomSource.create(seed * 31L + (long) age * 977L + b * 131L);
            double speed = 6.0 + b * 1.7;                       // blocks per tick
            double start = back + ((age * speed + b * len / bolts) % len);
            double phase = rnd.nextDouble() * Math.PI * 2.0 + age * 1.3 * (b % 2 == 0 ? 1 : -1);
            int pieces = (int) Math.max(8, window / 0.35);
            List<Vec3> pts = new ArrayList<>();
            for (int i = 0; i <= pieces; i++) {
                double d = start + window * i / pieces;
                if (d > front) {
                    break;
                }
                double ang = phase + d * 1.9;
                double r = beamRadius * (1.4 + rnd.nextDouble() * 0.9);
                pts.add(dir.scale(d).add(u.scale(Math.cos(ang) * r)).add(v.scale(Math.sin(ang) * r)));
                if (i % 5 == 3) { // short crackling fork
                    Vec3 p = pts.get(pts.size() - 1);
                    Vec3 out = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang))).scale(beamRadius * (1.0 + rnd.nextDouble() * 2.0));
                    List<Vec3> fork = List.of(p, p.add(out).add(dir.scale((rnd.nextDouble() - 0.3) * 0.8)));
                    strikeRaw(vc, pose, fork, beamRadius * 0.1F, 230);
                }
            }
            if (pts.size() > 1) {
                strikeRaw(vc, pose, pts, beamRadius * 0.16F, 255);
            }
        }
    }

    private static void strikeRaw(VertexConsumer vc, PoseStack.Pose pose, List<Vec3> pts, float width, int alpha) {
        polyline(vc, pose, pts, width * 1.8F, GLOW, (int) (alpha * 0.65F));
        polyline(vc, pose, pts, width * 0.7F, CORE, alpha);
    }

    private static Vec3 randomDir(RandomSource rnd) {
        for (int i = 0; i < 8; i++) {
            Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian());
            if (d.lengthSqr() > 1.0E-4) {
                return d.normalize();
            }
        }
        return new Vec3(0.0, 1.0, 0.0);
    }

    /** Two crossed thin quads per segment, drawn from both sides, so the bolt is visible from every angle. */
    private static void polyline(VertexConsumer vc, PoseStack.Pose pose, List<Vec3> pts, float width, int[] rgb, int alpha) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 a = pts.get(i);
            Vec3 b = pts.get(i + 1);
            Vec3 d = b.subtract(a);
            if (d.lengthSqr() < 1.0E-8) {
                continue;
            }
            d = d.normalize();
            Vec3 ref = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 s = d.cross(ref).normalize().scale(width);
            Vec3 t = d.cross(s).normalize().scale(width);
            quadBoth(vc, pose, a.add(s), b.add(s), b.subtract(s), a.subtract(s), rgb, alpha);
            quadBoth(vc, pose, a.add(t), b.add(t), b.subtract(t), a.subtract(t), rgb, alpha);
        }
    }

    private static void quadBoth(VertexConsumer vc, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int[] rgb, int alpha) {
        vertex(vc, pose, p0, rgb, alpha);
        vertex(vc, pose, p1, rgb, alpha);
        vertex(vc, pose, p2, rgb, alpha);
        vertex(vc, pose, p3, rgb, alpha);
        vertex(vc, pose, p3, rgb, alpha);
        vertex(vc, pose, p2, rgb, alpha);
        vertex(vc, pose, p1, rgb, alpha);
        vertex(vc, pose, p0, rgb, alpha);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, int[] rgb, int alpha) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(rgb[0], rgb[1], rgb[2], alpha);
    }
}
