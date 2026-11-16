package com.hellgauntlet.boss;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = TheCorruptedMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {
    private ModNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(CorruptionSyncPayload.TYPE, CorruptionSyncPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientFxState.setCorrupted(payload.entityId(), payload.active(), context.player().level().getGameTime())));
        registrar.playToClient(CorruptionAttackFxPayload.TYPE, CorruptionAttackFxPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientFxState.addAttack(payload, context.player().level().getGameTime())));
        registrar.playToClient(LightningArcPayload.TYPE, LightningArcPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.hellgauntlet.boss.client.ClientLightningArcs.addArc(payload.x1(), payload.y1(), payload.z1(),
                                payload.x2(), payload.y2(), payload.z2(), payload.seed())));
    }
}
