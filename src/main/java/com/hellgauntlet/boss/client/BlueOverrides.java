package com.hellgauntlet.boss.client;

import com.hellgauntlet.boss.AbyssalStormGauntletItem;
import com.hellgauntlet.boss.TheCorruptedMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the Hellgauntlet's Void Orb, laser beam and blast sphere in electric blue when they come from the Fused
 * Blue-Lightning Gauntlet. The boss mod loads AFTER hellgauntlet (see neoforge.mods.toml), so these renderers replace the
 * red ones; for anything that is not from the fused gauntlet they hand over to the original renderer, built by
 * reflection, so the base Hellgauntlet looks exactly as before.
 */
@EventBusSubscriber(modid = TheCorruptedMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BlueOverrides {
    private static final String NS = "hellgauntlet";
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "textures/entity/void_sphere.png");
    /** entity id -> decided once, when the entity is first drawn. */
    static final Map<Integer, Boolean> DECIDED = new HashMap<>();

    private BlueOverrides() {}

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        register(event, "void_orb", "com.hellgauntlet.VoidOrbRenderer", 0);
        register(event, "laser_beam", "com.hellgauntlet.LaserBeamRenderer", 1);
        register(event, "void_sphere", "com.hellgauntlet.VoidSphereRenderer", 2);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(EntityRenderersEvent.RegisterRenderers event, String entity, String rendererClass, int kind) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NS, entity);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return; // Hellgauntlet is not installed
        }
        EntityType type = BuiltInRegistries.ENTITY_TYPE.get(id);
        event.registerEntityRenderer(type, (EntityRendererProvider) ctx -> new Override(ctx, rendererClass, kind));
    }

    static boolean holdsFused(Player player) {
        return player.getMainHandItem().getItem() instanceof AbyssalStormGauntletItem
                || player.getOffhandItem().getItem() instanceof AbyssalStormGauntletItem;
    }

    private static boolean decide(Entity entity, int kind) {
        if (kind == 0) {
            return entity instanceof Projectile p && p.getOwner() instanceof Player owner && holdsFused(owner);
        }
        if (kind == 1) {
            for (Player p : entity.level().players()) {
                if (p.distanceToSqr(entity) < 4.5 * 4.5 && holdsFused(p)) {
                    return true;
                }
            }
            return false;
        }
        return BlueTracker.isBlueSpot(entity.position(), entity.level().getGameTime());
    }

    /** One renderer class for orb (0), laser (1) and blast sphere (2). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class Override extends EntityRenderer<Entity> {
        private final EntityRenderer delegate;
        private final int kind;

        Override(EntityRendererProvider.Context context, String rendererClass, int kind) {
            super(context);
            this.kind = kind;
            EntityRenderer made = null;
            try {
                made = (EntityRenderer) Class.forName(rendererClass).getConstructor(EntityRendererProvider.Context.class).newInstance(context);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // no original renderer: only the blue drawing is available
            }
            this.delegate = made;
        }

        @Override
        public boolean shouldRender(Entity entity, Frustum frustum, double x, double y, double z) {
            return this.delegate != null ? this.delegate.shouldRender(entity, frustum, x, y, z) : super.shouldRender(entity, frustum, x, y, z);
        }

        @Override
        public ResourceLocation getTextureLocation(Entity entity) {
            return this.delegate != null ? this.delegate.getTextureLocation(entity) : TEXTURE;
        }

        @Override
        public void render(Entity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
            if (DECIDED.size() > 512) {
                DECIDED.clear();
            }
            boolean blue = DECIDED.computeIfAbsent(entity.getId(), id -> decide(entity, this.kind));
            if (!blue) {
                if (this.delegate != null) {
                    this.delegate.render(entity, yaw, partialTick, poseStack, buffer, light);
                }
                return;
            }
            switch (this.kind) {
                case 0 -> drawOrb(entity, poseStack, buffer);
                case 1 -> {
                    if (this.delegate != null) {
                        this.delegate.render(entity, yaw, partialTick, poseStack, new RecolorBuffer(buffer), light);
                    }
                    drawBeamSpirals(entity, partialTick, poseStack, buffer);
                }
                default -> {
                    BlueTracker.mark(entity.position(), entity.level().getGameTime());
                    if (this.delegate != null) {
                        this.delegate.render(entity, yaw, partialTick, poseStack, new RecolorBuffer(buffer), light);
                    }
                }
            }
        }

        private void drawOrb(Entity entity, PoseStack poseStack, MultiBufferSource buffer) {
            float r = 0.34F;
            poseStack.pushPose();
            poseStack.translate(0.0F, entity.getBbHeight() / 2.0F, 0.0F);
            VertexConsumer core = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
            BossVoidFx.sphere(core, poseStack.last(), r, 12, 18, 255);
            VertexConsumer additive = buffer.getBuffer(RenderType.lightning());
            poseStack.pushPose();
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            BlueVoidFx.haloRing(additive, poseStack.last(), r * 0.97F, r * 2.1F, 170);
            poseStack.popPose();
            // a new shape every tick and 7 arcs (the red orb redraws every 2 ticks with 4)
            BlueVoidFx.sphereArcs(additive, poseStack.last(), r, 7, entity.getId(), entity.tickCount, r * 0.09F);
            poseStack.popPose();
        }

        /** Fast spiralling blue bolts with side forks on top of the (recoloured) beam. */
        private void drawBeamSpirals(Entity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
            try {
                float age = entity.tickCount + partialTick;
                float length = (Float) Refl.call(entity, "getLength");
                int fadeStart = (Integer) Refl.call(entity, "getFadeStart");
                boolean stream = (Boolean) Refl.call(entity, "isStream");
                Class<?> c = entity.getClass();
                float head;
                float tail = 0.0F;
                if (stream) {
                    head = (Float) Refl.callStatic(c, "streamHead", new Class<?>[]{float.class, float.class}, length, age);
                    tail = (Float) Refl.callStatic(c, "streamTail", new Class<?>[]{float.class, int.class, float.class}, head, fadeStart, age);
                } else {
                    head = (Float) Refl.callStatic(c, "headLength", new Class<?>[]{float.class, float.class}, length, age);
                    if (fadeStart >= 0 && age > fadeStart + 8) {
                        return; // beam has faded out
                    }
                }
                Vec3 dir = entity.getLookAngle();
                BlueTracker.mark(entity.position().add(dir.scale(head)), entity.level().getGameTime());
                // same rule as the beam itself: nothing right around the camera
                Vec3 camRel = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(entity.getPosition(partialTick));
                double along = camRel.dot(dir);
                if (camRel.subtract(dir.scale(along)).length() < 1.5 && along > -3.0) {
                    tail = (float) Math.max(tail, along + 3.0);
                }
                Vec3 up = Math.abs(dir.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
                Vec3 u = dir.cross(up).normalize();
                Vec3 v = dir.cross(u).normalize();
                BlueVoidFx.beamSpirals(buffer.getBuffer(RenderType.lightning()), poseStack.last(), dir, u, v, tail, head,
                        0.3F, 5, entity.getId(), age);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // spirals are an extra; the recoloured beam is already drawn
            }
        }
    }

    /** Tiny reflection helpers for the Hellgauntlet's beam entity (separate mod, no compile dependency). */
    private static final class Refl {
        private Refl() {}

        static Object call(Object target, String name) throws ReflectiveOperationException {
            Method m = target.getClass().getMethod(name);
            return m.invoke(target);
        }

        static Object callStatic(Class<?> type, String name, Class<?>[] params, Object... args) throws ReflectiveOperationException {
            return type.getMethod(name, params).invoke(null, args);
        }
    }
}
