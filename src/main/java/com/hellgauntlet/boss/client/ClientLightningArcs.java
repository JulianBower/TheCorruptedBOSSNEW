package com.hellgauntlet.boss.client;

import com.hellgauntlet.boss.TheCorruptedMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/** Active red lightning arcs (enchantment effects); each lives 8 ticks and fades out. Drawn as real 3D jagged bolts. */
@EventBusSubscriber(modid = TheCorruptedMod.MODID, value = Dist.CLIENT)
public final class ClientLightningArcs {
    private static final int DURATION = 8;

    private static final class Arc {
        final Vec3 from;
        final Vec3 to;
        final long seed;
        final int style;
        final float scale;
        int ticksLeft = DURATION;

        Arc(Vec3 from, Vec3 to, long seed, int style, float scale) {
            this.from = from;
            this.to = to;
            this.seed = seed;
            this.style = style;
            this.scale = scale;
        }
    }

    private static final List<Arc> ARCS = new ArrayList<>();

    private ClientLightningArcs() {}

    public static void addArc(double x1, double y1, double z1, double x2, double y2, double z2, long seed,
                              int style, float scale) {
        ARCS.add(new Arc(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), seed, style, scale));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            ARCS.clear();
            return;
        }
        if (!Minecraft.getInstance().isPaused()) {
            ARCS.removeIf(a -> --a.ticksLeft <= 0);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || ARCS.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        for (Arc arc : ARCS) {
            float fade = arc.ticksLeft / (float) DURATION;
            if (arc.style == 1) {
                // electric blue: sharper jitter, white-cyan core inside a sky-blue glow
                BlueVoidFx.strike(vc, poseStack.last(),
                        BossVoidFx.jaggedPath(RandomSource.create(arc.seed), arc.from, arc.to, 10, 0.2), 1.2F * arc.scale, fade);
            } else {
                BossVoidFx.strike(vc, poseStack.last(),
                        BossVoidFx.jaggedPath(RandomSource.create(arc.seed), arc.from, arc.to, 8, 0.14), 1.2F * arc.scale, fade);
            }
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lightning());
    }
}
