package com.hellgauntlet.boss;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Tells clients whether an entity currently has Corruption (vanilla only syncs effects of players). */
public record CorruptionSyncPayload(int entityId, boolean active) implements CustomPacketPayload {
    public static final Type<CorruptionSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "corruption_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CorruptionSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CorruptionSyncPayload::entityId,
            ByteBufCodecs.BOOL, CorruptionSyncPayload::active,
            CorruptionSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
