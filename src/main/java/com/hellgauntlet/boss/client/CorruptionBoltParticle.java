package com.hellgauntlet.boss.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** A short, jagged red lightning bolt that snaps to a new shape every tick. Fullbright. */
public class CorruptionBoltParticle extends TextureSheetParticle {
    private final long seed;
    private final double length;

    protected CorruptionBoltParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.pickSprite(sprites);
        this.lifetime = 6 + this.random.nextInt(5);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.seed = this.random.nextLong();
        this.length = 1.4 + this.random.nextDouble() * 1.4;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        double px = Mth.lerp(partialTicks, this.xo, this.x);
        double py = Mth.lerp(partialTicks, this.yo, this.y);
        double pz = Mth.lerp(partialTicks, this.zo, this.z);
        RandomSource rnd = RandomSource.create(this.seed + this.age * 7919L);
        Vec3 dir = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.8, rnd.nextGaussian());
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : dir.normalize();
        Vec3 perp = dir.cross(new Vec3(0.0, 1.0, 0.1)).normalize();
        float fade = 1.0F - (float) this.age / this.lifetime * 0.5F;

        int segments = 4;
        Vec3[] pts = new Vec3[segments + 1];
        Vec3 start = dir.scale(-this.length * 0.5);
        for (int i = 0; i <= segments; i++) {
            Vec3 p = start.add(dir.scale(this.length * i / segments));
            if (i != 0 && i != segments) {
                p = p.add(perp.scale((rnd.nextDouble() - 0.5) * this.length * 0.7))
                        .add(0.0, (rnd.nextDouble() - 0.5) * this.length * 0.4, 0.0);
            }
            pts[i] = p.add(px - cam.x, py - cam.y, pz - cam.z);
        }
        float u = (this.getU0() + this.getU1()) * 0.5F;
        float v = (this.getV0() + this.getV1()) * 0.5F;
        for (int i = 0; i < segments; i++) {
            ribbon(buffer, pts[i], pts[i + 1], 0.16F, 217, 30, 24, (int) (200 * fade), u, v);
            ribbon(buffer, pts[i], pts[i + 1], 0.055F, 255, 110, 100, (int) (255 * fade), u, v);
        }
    }

    private static void ribbon(VertexConsumer vc, Vec3 a, Vec3 b, float width, int r, int g, int bl, int alpha, float u, float v) {
        Vec3 side = b.subtract(a).cross(a.add(b).scale(0.5));
        side = (side.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 1.0, 0.0) : side.normalize()).scale(width);
        quad(vc, a.add(side), b.add(side), b.subtract(side), a.subtract(side), r, g, bl, alpha, u, v);
    }

    private static void quad(VertexConsumer vc, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int r, int g, int b, int a, float u, float v) {
        for (Vec3 p : new Vec3[] {p0, p1, p2, p3}) {
            vc.addVertex((float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(r, g, b, a).setLight(15728880);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new CorruptionBoltParticle(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }
}
