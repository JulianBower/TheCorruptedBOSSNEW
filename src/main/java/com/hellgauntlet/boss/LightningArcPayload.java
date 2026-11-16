package com.hellgauntlet.boss;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One jagged 3D red lightning arc from (x1,y1,z1) to (x2,y2,z2); `seed` makes every client draw the same bolt. */
public record LightningArcPayload(double x1, double y1, double z1, double x2, double y2, double z2, long seed)
        implements CustomPacketPayload {
    public static final Type<LightningArcPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "lightning_arc"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LightningArcPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.x1);
                buf.writeDouble(p.y1);
                buf.writeDouble(p.z1);
                buf.writeDouble(p.x2);
                buf.writeDouble(p.y2);
                buf.writeDouble(p.z2);
                buf.writeLong(p.seed);
            },
            buf -> new LightningArcPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
