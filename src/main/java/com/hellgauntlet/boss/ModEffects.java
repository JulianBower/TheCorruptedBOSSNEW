package com.hellgauntlet.boss;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, TheCorruptedMod.MODID);

    public static final DeferredHolder<MobEffect, CorruptionEffect> CORRUPTION =
            EFFECTS.register("corruption", CorruptionEffect::new);

    private ModEffects() {}
}
