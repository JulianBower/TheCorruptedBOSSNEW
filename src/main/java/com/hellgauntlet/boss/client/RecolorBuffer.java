package com.hellgauntlet.boss.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Wraps a buffer source so the Hellgauntlet's additive dark-red glow and lightning (the exact colour 140,0,20 on the
 * lightning render type) come out in sky blue (#38BDF8). The black core passes through untouched.
 */
final class RecolorBuffer implements MultiBufferSource {
    private final MultiBufferSource inner;

    RecolorBuffer(MultiBufferSource inner) {
        this.inner = inner;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        VertexConsumer vc = this.inner.getBuffer(type);
        return type == RenderType.lightning() ? new Recolor(vc) : vc;
    }

    private static final class Recolor implements VertexConsumer {
        private final VertexConsumer vc;

        Recolor(VertexConsumer vc) {
            this.vc = vc;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.vc.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            if (r == 140 && g == 0 && b == 20) {
                this.vc.setColor(56, 189, 248, a);
            } else {
                this.vc.setColor(r, g, b, a);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.vc.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.vc.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.vc.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.vc.setNormal(x, y, z);
            return this;
        }
    }
}
