package com.hellgauntlet.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Drawing helpers for the boss: black orb core, crimson halo, jagged lightning. Adapted from VoidFx of the gauntlet
 * mod; every vertex carries the full entity vertex format so the same code works with RenderType.lightning() and
 * with RenderType.entityTranslucentEmissive().
 */
public final class BossVoidFx {
    private BossVoidFx() {}

    public static void sphere(VertexConsumer vc, PoseStack.Pose pose, float radius, int stacks, int slices, int alpha) {
        for (int i = 0; i < stacks; i++) {
            double phi0 = Math.PI * i / stacks;
            double phi1 = Math.PI * (i + 1) / stacks;
            for (int j = 0; j < slices; j++) {
                double t0 = 2.0 * Math.PI * j / slices;
                double t1 = 2.0 * Math.PI * (j + 1) / slices;
                black(vc, pose, radius, phi0, t0, alpha);
                black(vc, pose, radius, phi0, t1, alpha);
                black(vc, pose, radius, phi1, t1, alpha);
                black(vc, pose, radius, phi1, t0, alpha);
            }
        }
    }

    private static void black(VertexConsumer vc, PoseStack.Pose pose, float radius, double phi, double theta, int alpha) {
        float nx = (float) (Math.sin(phi) * Math.cos(theta));
        float ny = (float) Math.cos(phi);
        float nz = (float) (Math.sin(phi) * Math.sin(theta));
        vc.addVertex(pose, nx * radius, ny * radius, nz * radius)
                .setColor(0, 0, 0, alpha)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    /** Ring facing the camera (pose already rotated to the camera), strongest at the inner edge. */
    public static void haloRing(VertexConsumer vc, PoseStack.Pose pose, float inner, float outer, int alpha) {
        int n = 28;
        for (int i = 0; i < n; i++) {
            double a0 = 2.0 * Math.PI * i / n;
            double a1 = 2.0 * Math.PI * (i + 1) / n;
            Vec3 i0 = new Vec3(Math.cos(a0) * inner, Math.sin(a0) * inner, 0.0);
            Vec3 i1 = new Vec3(Math.cos(a1) * inner, Math.sin(a1) * inner, 0.0);
            Vec3 o0 = new Vec3(Math.cos(a0) * outer, Math.sin(a0) * outer, 0.0);
            Vec3 o1 = new Vec3(Math.cos(a1) * outer, Math.sin(a1) * outer, 0.0);
            quadBoth(vc, pose, i0, o0, o1, i1, 140, 0, 20, alpha, 0);
        }
    }

    /** `count` arcs hugging a sphere; new shape every 2 ticks (VoidFx.sphereArcs). */
    public static void sphereArcs(VertexConsumer vc, PoseStack.Pose pose, float radius, int count, long seed, int tick,
                                  float width, int alpha) {
        long step = tick / 2;
        for (int arc = 0; arc < count; arc++) {
            RandomSource rnd = RandomSource.create(seed * 341873128712L + step * 132897987541L + arc * 7919L);
            Vec3 a = randomDir(rnd);
            Vec3 tangent = a.cross(randomDir(rnd)).normalize();
            double span = 0.8 + rnd.nextDouble() * 0.7;
            int segments = Math.max(6, Math.min(24, (int) (radius * span / 0.6)));
            double lift = Math.min(radius * 0.08, 0.3);
            double wobble = Math.min(radius * 0.18, 0.45);
            List<Vec3> pts = new ArrayList<>();
            for (int i = 0; i <= segments; i++) {
                double t = (double) i / segments;
                double ang = (t - 0.5) * span;
                Vec3 dir = a.scale(Math.cos(ang)).add(tangent.scale(Math.sin(ang)));
                double r = radius + lift + rnd.nextDouble() * Math.min(radius * 0.3, 0.6);
                Vec3 jitter = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).scale(wobble);
                pts.add(dir.scale(r).add(i == 0 || i == segments ? Vec3.ZERO : jitter));
            }
            polyline(vc, pose, pts, width, 140, 0, 20, alpha);
        }
    }

    /** One jagged bolt from `from` to `to` (used for the tether between the raised gauntlet and the orb). */
    public static void tether(VertexConsumer vc, PoseStack.Pose pose, Vec3 from, Vec3 to, long seed, int tick, float width, int alpha) {
        RandomSource rnd = RandomSource.create(seed * 341873128712L + (tick / 2) * 132897987541L + 31L);
        polyline(vc, pose, jagged(rnd, from, to, 8, 0.12), width, 140, 0, 20, alpha);
    }

    /**
     * Corruption lightning around a body of the given size (origin = feet centre): jagged bolts with forks that
     * jump to a new shape every tick. Drawn twice: a wide dim crimson glow and a thin hot core.
     */
    public static void bodyBolts(VertexConsumer vc, PoseStack.Pose pose, float width, float height, long seed, int tick, int amplifier) {
        int count = 5 + amplifier * 2;
        for (int i = 0; i < count; i++) {
            RandomSource rnd = RandomSource.create(seed * 341873128712L + tick * 132897987541L + i * 7919L);
            if (rnd.nextFloat() < 0.25F) {
                continue; // bolts snap in and out
            }
            Vec3 a = shellPoint(rnd, width, height);
            Vec3 b = shellPoint(rnd, width, height);
            if (a.distanceToSqr(b) < 0.3) {
                continue;
            }
            List<Vec3> main = jagged(rnd, a, b, 5 + rnd.nextInt(4), 0.2);
            bolt(vc, pose, main, 1.0F);
            for (int j = 1; j < main.size() - 1; j++) {
                if (rnd.nextFloat() < 0.4F) {
                    Vec3 start = main.get(j);
                    Vec3 away = new Vec3(start.x, start.y - height * 0.5, start.z);
                    away = away.lengthSqr() < 1.0E-4 ? randomDir(rnd) : away.normalize();
                    Vec3 dir = away.scale(0.5).add(randomDir(rnd).scale(0.7)).normalize();
                    Vec3 end = start.add(dir.scale(0.4 + rnd.nextDouble() * 0.6));
                    bolt(vc, pose, jagged(rnd, start, end, 3, 0.15), 0.55F);
                }
            }
        }
    }

    /** Jagged path between two points (VoidFx lightning math), for attack effects. */
    public static List<Vec3> jaggedPath(RandomSource rnd, Vec3 a, Vec3 b, int segments, double jitterFactor) {
        return jagged(rnd, a, b, segments, jitterFactor);
    }

    /** Red (255, 30, 35) lightning strike along `pts`: wide glow plus a hot core; fade 0..1. */
    public static void strike(VertexConsumer vc, PoseStack.Pose pose, List<Vec3> pts, float scale, float fade) {
        polyline(vc, pose, pts, 0.07F * scale, 255, 30, 35, (int) (150 * fade));
        polyline(vc, pose, pts, 0.022F * scale, 255, 130, 125, (int) (255 * fade));
    }

    public static Vec3 randomUnit(RandomSource rnd) {
        return randomDir(rnd);
    }

    private static void bolt(VertexConsumer vc, PoseStack.Pose pose, List<Vec3> pts, float scale) {
        polyline(vc, pose, pts, 0.05F * scale, 217, 30, 24, 110);
        polyline(vc, pose, pts, 0.016F * scale, 255, 96, 84, 255);
    }

    private static Vec3 shellPoint(RandomSource rnd, float width, float height) {
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        double y = height * (0.03 + rnd.nextDouble() * 0.94);
        double r = width * (0.55 + rnd.nextDouble() * 0.35);
        return new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r);
    }

    private static List<Vec3> jagged(RandomSource rnd, Vec3 a, Vec3 b, int segments, double jitterFactor) {
        List<Vec3> pts = new ArrayList<>();
        double len = a.distanceTo(b);
        for (int i = 0; i <= segments; i++) {
            Vec3 p = a.lerp(b, (double) i / segments);
            if (i != 0 && i != segments) {
                p = p.add(new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).scale(len * jitterFactor * 2.0));
            }
            pts.add(p);
        }
        return pts;
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

    /** A line of two crossed thin quads per segment, visible from every side. */
    private static void polyline(VertexConsumer vc, PoseStack.Pose pose, List<Vec3> pts, float width, int r, int g, int b, int alpha) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 a = pts.get(i);
            Vec3 c = pts.get(i + 1);
            Vec3 d = c.subtract(a);
            if (d.lengthSqr() < 1.0E-8) {
                continue;
            }
            d = d.normalize();
            Vec3 ref = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 s = d.cross(ref).normalize().scale(width);
            Vec3 t = d.cross(s).normalize().scale(width);
            quadBoth(vc, pose, a.add(s), c.add(s), c.subtract(s), a.subtract(s), r, g, b, alpha, alpha);
            quadBoth(vc, pose, a.add(t), c.add(t), c.subtract(t), a.subtract(t), r, g, b, alpha, alpha);
        }
    }

    /** Quad drawn on both sides. alphaA applies to p0 and p3, alphaB to p1 and p2. */
    private static void quadBoth(VertexConsumer vc, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                                 int r, int g, int b, int alphaA, int alphaB) {
        vertex(vc, pose, p0, r, g, b, alphaA);
        vertex(vc, pose, p1, r, g, b, alphaB);
        vertex(vc, pose, p2, r, g, b, alphaB);
        vertex(vc, pose, p3, r, g, b, alphaA);
        vertex(vc, pose, p3, r, g, b, alphaA);
        vertex(vc, pose, p2, r, g, b, alphaB);
        vertex(vc, pose, p1, r, g, b, alphaB);
        vertex(vc, pose, p0, r, g, b, alphaA);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, int r, int g, int b, int alpha) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(r, g, b, alpha)
                .setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
