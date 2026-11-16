package com.hellgauntlet.boss.client;

import com.hellgauntlet.boss.ModEntities;
import com.hellgauntlet.boss.TheCorruptedMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client registration for the Little Demon (kept out of ClientSetup so that file stays untouched). */
@EventBusSubscriber(modid = TheCorruptedMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LittleDemonClientSetup {
    private LittleDemonClientSetup() {}

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LittleDemonModel.LAYER, LittleDemonModel::createLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.LITTLE_DEMON.get(), LittleDemonRenderer::new);
    }
}
