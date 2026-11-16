package com.hellgauntlet.boss;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Red lightning effect of a boss attack. 1 = shockwave ring, 2 = void rupture line, 3 = dash / slam, 4 = phase 2 awakening burst. */
public record CorruptionAttackFxPayload(int attackType, double ox, double oy, double oz, double dx, double dy, double dz)
        implements CustomPacketPayload {
    public static final Type<CorruptionAttackFxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "corruption_attack_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CorruptionAttackFxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.attackType);
                buf.writeDouble(p.ox);
                buf.writeDouble(p.oy);
                buf.writeDouble(p.oz);
                buf.writeDouble(p.dx);
                buf.writeDouble(p.dy);
                buf.writeDouble(p.dz);
            },
            buf -> new CorruptionAttackFxPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
