package com.hellgauntlet.boss;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, TheCorruptedMod.MODID);

    /** One short jagged red lightning bolt; replaces every red dust particle of the boss. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CORRUPTION_BOLT =
            PARTICLES.register("corruption_bolt", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
