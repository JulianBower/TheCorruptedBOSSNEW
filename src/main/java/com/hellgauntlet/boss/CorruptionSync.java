package com.hellgauntlet.boss;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server side: keeps every client informed about which entities carry Corruption. */
@EventBusSubscriber(modid = TheCorruptedMod.MODID)
public final class CorruptionSync {
    private CorruptionSync() {}

    private static boolean isCorruption(MobEffectInstance instance) {
        return instance != null && instance.getEffect().is(ModEffects.CORRUPTION.getId());
    }

    private static void broadcast(LivingEntity entity, boolean active) {
        if (!entity.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new CorruptionSyncPayload(entity.getId(), active));
        }
    }

    @SubscribeEvent
    public static void onAdded(MobEffectEvent.Added event) {
        if (isCorruption(event.getEffectInstance())) {
            broadcast(event.getEntity(), true);
        }
    }

    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        if (isCorruption(event.getEffectInstance())) {
            broadcast(event.getEntity(), false);
        }
    }

    @SubscribeEvent
    public static void onRemove(MobEffectEvent.Remove event) {
        if (isCorruption(event.getEffectInstance())) {
            broadcast(event.getEntity(), false);
        }
    }

    /** Someone walks into range of an already corrupted entity. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity target && event.getEntity() instanceof ServerPlayer player
                && target.hasEffect(ModEffects.CORRUPTION)) {
            PacketDistributor.sendToPlayer(player, new CorruptionSyncPayload(target.getId(), true));
        }
    }
}
