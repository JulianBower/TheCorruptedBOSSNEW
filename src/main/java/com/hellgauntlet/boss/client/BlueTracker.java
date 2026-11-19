package com.hellgauntlet.boss.client;

import com.hellgauntlet.boss.TheCorruptedMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.ArrayDeque;

/**
 * Remembers where blue (Fused gauntlet) orbs and beams ended, so the blast sphere that appears there is drawn blue
 * too (the sphere entity itself carries no owner).
 */
@EventBusSubscriber(modid = TheCorruptedMod.MODID, value = Dist.CLIENT)
public final class BlueTracker {
    private static final ResourceLocation ORB = ResourceLocation.fromNamespaceAndPath("hellgauntlet", "void_orb");
    private static final int WINDOW_TICKS = 40;
    private static final double RANGE = 10.0;

    private record Spot(Vec3 pos, long time) {}

    private static final ArrayDeque<Spot> SPOTS = new ArrayDeque<>();

    private BlueTracker() {}

    static void mark(Vec3 pos, long time) {
        SPOTS.addLast(new Spot(pos, time));
        while (SPOTS.size() > 64) {
            SPOTS.removeFirst();
        }
    }

    static boolean isBlueSpot(Vec3 pos, long now) {
        for (Spot s : SPOTS) {
            if (now - s.time <= WINDOW_TICKS && s.pos.distanceToSqr(pos) <= RANGE * RANGE) {
                return true;
            }
        }
        return false;
    }

    /** A blue orb that hit something: remember the spot. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide && ORB.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))
                && Boolean.TRUE.equals(BlueOverrides.DECIDED.get(e.getId()))) {
            mark(e.position(), e.level().getGameTime());
        }
        BlueOverrides.DECIDED.remove(e.getId());
    }
}
